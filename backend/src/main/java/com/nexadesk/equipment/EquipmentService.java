package com.nexadesk.equipment;

import com.nexadesk.audit.AuditAction;
import com.nexadesk.audit.AuditService;
import com.nexadesk.common.web.PageResponse;
import com.nexadesk.equipment.dto.EquipmentDtos.AssignmentDto;
import com.nexadesk.equipment.dto.EquipmentDtos.EquipmentDto;
import com.nexadesk.equipment.dto.EquipmentDtos.EquipmentFilter;
import com.nexadesk.equipment.dto.EquipmentDtos.EquipmentRefDto;
import com.nexadesk.equipment.dto.EquipmentDtos.EquipmentRequest;
import com.nexadesk.exception.BusinessException;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.exception.ResourceNotFoundException;
import com.nexadesk.ticket.TicketAccess;
import com.nexadesk.ticket.TicketMapper;
import com.nexadesk.ticket.TicketPolicy;
import com.nexadesk.ticket.TicketRepository;
import com.nexadesk.ticket.dto.TicketDtos.TicketSummaryDto;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.User;
import com.nexadesk.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * IT asset inventory. Lifecycle: IN_STOCK ⇄ IN_USE (assigned to someone or shared at a location) ⇄ IN_REPAIR,
 * then RETIRED (final, read-only). Every assignment is kept in {@link EquipmentAssignment}.
 */
@Slf4j
@Service
public class EquipmentService {

    private final EquipmentRepository repository;
    private final EquipmentAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final TicketAccess access;
    private final TicketPolicy ticketPolicy;
    private final TicketMapper ticketMapper;
    private final JdbcClient jdbc;
    private final AuditService audit;
    private final Clock clock;
    private final ZoneId zone;

    private static final String AUDITED = "EQUIPMENT";

    public EquipmentService(EquipmentRepository repository, EquipmentAssignmentRepository assignmentRepository,
                            UserRepository userRepository, TicketRepository ticketRepository, TicketAccess access,
                            TicketPolicy ticketPolicy, TicketMapper ticketMapper, JdbcClient jdbc, AuditService audit,
                            Clock clock, @Value("${nexadesk.time-zone}") ZoneId zone) {
        this.repository = repository;
        this.assignmentRepository = assignmentRepository;
        this.userRepository = userRepository;
        this.ticketRepository = ticketRepository;
        this.access = access;
        this.ticketPolicy = ticketPolicy;
        this.ticketMapper = ticketMapper;
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
        this.zone = zone;
    }

    /** Employees only ever see the devices they hold. */
    @Transactional(readOnly = true)
    public PageResponse<EquipmentDto> list(EquipmentFilter filter, Pageable pageable) {
        User actor = access.actor();
        EquipmentFilter effective = isEmployee(actor)
                ? new EquipmentFilter(filter.search(), filter.type(), filter.status(), actor.getId())
                : filter;
        return PageResponse.from(repository.findAll(EquipmentSpecifications.matching(effective), pageable)
                .map(this::toDto));
    }

    @Transactional(readOnly = true)
    public EquipmentDto get(Long id) {
        User actor = access.actor();
        Equipment equipment = find(id);
        if (isEmployee(actor) && !heldBy(equipment, actor)) {
            throw new ResourceNotFoundException("Équipement", id);
        }
        return toDto(equipment);
    }

    /** Devices the current user may attach to a ticket (RM-14). */
    @Transactional(readOnly = true)
    public List<EquipmentRefDto> selectable() {
        User actor = access.actor();
        List<Equipment> devices = isEmployee(actor)
                ? repository.findSelectableBy(actor.getId())
                : repository.findAllNotRetired();
        return devices.stream().map(EquipmentRefDto::from).toList();
    }

    @Transactional
    public EquipmentDto create(EquipmentRequest request) {
        validateDates(request);
        String serial = normalizeSerial(request.serialNumber());
        if (serial != null && repository.existsBySerialNumber(serial)) {
            throw new BusinessException(ErrorCode.SERIAL_NUMBER_ALREADY_USED);
        }
        long next = jdbc.sql("select nextval('equipment_code_seq')").query(Long.class).single();
        Equipment equipment = new Equipment("EQ-%04d".formatted(next), request.name().trim(), request.type());
        apply(equipment, request, serial);
        repository.save(equipment);
        audit.record(AuditAction.EQUIPMENT_CREATED, AUDITED, equipment.getInventoryCode(),
                "Ajout de " + equipment.getInventoryCode() + " – " + equipment.getName(), null, snapshot(equipment));
        return toDto(equipment);
    }

    @Transactional
    public EquipmentDto update(Long id, EquipmentRequest request) {
        Equipment equipment = findEditable(id);
        validateDates(request);
        String serial = normalizeSerial(request.serialNumber());
        if (serial != null && repository.existsBySerialNumberAndIdNot(serial, id)) {
            throw new BusinessException(ErrorCode.SERIAL_NUMBER_ALREADY_USED);
        }
        Map<String, Object> before = snapshot(equipment);
        equipment.setName(request.name().trim());
        equipment.setType(request.type());
        apply(equipment, request, serial);
        repository.flush();
        audit.record(AuditAction.EQUIPMENT_UPDATED, AUDITED, equipment.getInventoryCode(),
                "Modification de " + equipment.getInventoryCode(), before, snapshot(equipment));
        return toDto(equipment);
    }

    /** Hands a device to a user (IN_USE), or returns it to stock when {@code userId} is null. */
    @Transactional
    public EquipmentDto assign(Long id, Long userId) {
        Equipment equipment = findEditable(id);
        if (equipment.getStatus() == EquipmentStatus.IN_REPAIR) {
            throw new BusinessException(ErrorCode.ACTION_NOT_ALLOWED,
                    "Un équipement en réparation ne peut pas être affecté.");
        }
        Instant now = clock.instant();
        if (userId != null && equipment.getAssignedUser() != null && equipment.getAssignedUser().getId().equals(userId)) {
            return toDto(equipment);
        }
        String previousHolder = holderEmail(equipment);
        closeOpenAssignment(equipment, now);
        if (userId == null) {
            equipment.setAssignedUser(null);
            equipment.setStatus(EquipmentStatus.IN_STOCK);
        } else {
            User user = userRepository.findById(userId).filter(User::isActive)
                    .orElseThrow(() -> new ResourceNotFoundException("Utilisateur actif", userId));
            equipment.setAssignedUser(user);
            equipment.setStatus(EquipmentStatus.IN_USE);
            assignmentRepository.save(new EquipmentAssignment(equipment, user, now));
        }
        repository.flush();
        audit.record(AuditAction.EQUIPMENT_ASSIGNED, AUDITED, equipment.getInventoryCode(),
                equipment.getInventoryCode() + (userId == null ? " remis en stock" : " affecté à " + holderEmail(equipment)),
                AuditService.values("holder", previousHolder), AuditService.values("holder", holderEmail(equipment)));
        return toDto(equipment);
    }

    /**
     * Status changes other than assignment and retirement: send to / back from repair, deploy as a shared device,
     * or put back in stock. A device in repair keeps its holder.
     */
    @Transactional
    public EquipmentDto changeStatus(Long id, EquipmentStatus target) {
        Equipment equipment = findEditable(id);
        EquipmentStatus current = equipment.getStatus();
        if (target == current) {
            return toDto(equipment);
        }
        switch (target) {
            case IN_REPAIR -> { }
            case IN_USE -> {
                if (equipment.getAssignedUser() == null && isBlank(equipment.getLocation())) {
                    throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                            "Un équipement partagé doit avoir un emplacement (ex. « 2e étage – salle copieurs »).");
                }
            }
            case IN_STOCK -> {
                if (equipment.getAssignedUser() != null) {
                    throw new BusinessException(ErrorCode.ACTION_NOT_ALLOWED,
                            "Retirez d'abord l'affectation pour remettre l'équipement en stock.");
                }
            }
            case RETIRED -> throw new BusinessException(ErrorCode.ACTION_NOT_ALLOWED,
                    "Utilisez la mise au rebut pour retirer un équipement du parc.");
        }
        equipment.setStatus(target);
        repository.flush();
        audit.record(AuditAction.EQUIPMENT_STATUS_CHANGED, AUDITED, equipment.getInventoryCode(),
                equipment.getInventoryCode() + " : " + current.label() + " → " + target.label(),
                AuditService.values("status", current), AuditService.values("status", target));
        return toDto(equipment);
    }

    /** Final: the device leaves the inventory; its current assignment is closed. */
    @Transactional
    public EquipmentDto retire(Long id) {
        Equipment equipment = findEditable(id);
        closeOpenAssignment(equipment, clock.instant());
        equipment.setAssignedUser(null);
        equipment.setStatus(EquipmentStatus.RETIRED);
        repository.flush();
        log.info("Equipment {} retired", equipment.getInventoryCode());
        audit.record(AuditAction.EQUIPMENT_RETIRED, AUDITED, equipment.getInventoryCode(),
                "Mise au rebut de " + equipment.getInventoryCode() + " – " + equipment.getName());
        return toDto(equipment);
    }

    private static String holderEmail(Equipment equipment) {
        return equipment.getAssignedUser() == null ? null : equipment.getAssignedUser().getEmail();
    }

    private static Map<String, Object> snapshot(Equipment e) {
        return AuditService.values("name", e.getName(), "type", e.getType(), "brand", e.getBrand(),
                "model", e.getModel(), "serialNumber", e.getSerialNumber(), "location", e.getLocation(),
                "acquisitionDate", e.getAcquisitionDate() == null ? null : e.getAcquisitionDate().toString(),
                "warrantyEndDate", e.getWarrantyEndDate() == null ? null : e.getWarrantyEndDate().toString());
    }

    @Transactional(readOnly = true)
    public List<AssignmentDto> assignments(Long id) {
        find(id);
        return assignmentRepository.findByEquipment_IdOrderByAssignedAtDescIdDesc(id).stream()
                .map(a -> new AssignmentDto(a.getId(), ticketMapper.user(a.getUser()), a.getAssignedAt(),
                        a.getReturnedAt()))
                .toList();
    }

    /** Incident history of the device, limited to the tickets the caller is allowed to see. */
    @Transactional(readOnly = true)
    public List<TicketSummaryDto> tickets(Long id) {
        User actor = access.actor();
        find(id);
        return ticketRepository.findByEquipment_IdOrderByCreatedAtDesc(id).stream()
                .filter(ticket -> ticketPolicy.canView(ticket, actor))
                .map(ticketMapper::toSummary)
                .toList();
    }

    private void closeOpenAssignment(Equipment equipment, Instant at) {
        assignmentRepository.findByEquipment_IdAndReturnedAtIsNull(equipment.getId())
                .ifPresent(assignment -> assignment.close(at));
        assignmentRepository.flush(); // the partial unique index allows one open assignment at a time
    }

    private Equipment find(Long id) {
        return repository.findWithUserById(id).orElseThrow(() -> new ResourceNotFoundException("Équipement", id));
    }

    private Equipment findEditable(Long id) {
        Equipment equipment = find(id);
        if (equipment.getStatus() == EquipmentStatus.RETIRED) {
            throw new BusinessException(ErrorCode.EQUIPMENT_RETIRED);
        }
        return equipment;
    }

    private static void apply(Equipment equipment, EquipmentRequest request, String serial) {
        equipment.setBrand(blankToNull(request.brand()));
        equipment.setModel(blankToNull(request.model()));
        equipment.setSerialNumber(serial);
        equipment.setAcquisitionDate(request.acquisitionDate());
        equipment.setWarrantyEndDate(request.warrantyEndDate());
        equipment.setLocation(blankToNull(request.location()));
        equipment.setNotes(blankToNull(request.notes()));
    }

    private void validateDates(EquipmentRequest request) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (request.acquisitionDate() != null && request.acquisitionDate().isAfter(today)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "La date d'acquisition ne peut pas être future.");
        }
        if (request.acquisitionDate() != null && request.warrantyEndDate() != null
                && request.warrantyEndDate().isBefore(request.acquisitionDate())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "La fin de garantie doit suivre la date d'acquisition.");
        }
    }

    private EquipmentDto toDto(Equipment e) {
        return new EquipmentDto(e.getId(), e.getInventoryCode(), e.getName(), e.getType(), e.getBrand(), e.getModel(),
                e.getSerialNumber(), e.getAcquisitionDate(), e.getWarrantyEndDate(),
                e.isUnderWarranty(LocalDate.now(clock.withZone(zone))), e.getStatus(), e.getLocation(),
                ticketMapper.user(e.getAssignedUser()), e.isShared(), e.getNotes(), e.getVersion(), e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private static boolean isEmployee(User user) {
        return user.getRoleCode() == RoleCode.EMPLOYEE;
    }

    private static boolean heldBy(Equipment equipment, User user) {
        return equipment.getAssignedUser() != null && equipment.getAssignedUser().getId().equals(user.getId());
    }

    private static String normalizeSerial(String serial) {
        return isBlank(serial) ? null : serial.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
