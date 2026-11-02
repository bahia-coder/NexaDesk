package com.nexadesk.demo;

import com.nexadesk.equipment.Equipment;
import com.nexadesk.equipment.EquipmentAssignment;
import com.nexadesk.equipment.EquipmentAssignmentRepository;
import com.nexadesk.equipment.EquipmentRepository;
import com.nexadesk.equipment.EquipmentStatus;
import com.nexadesk.equipment.EquipmentType;
import com.nexadesk.ticket.Ticket;
import com.nexadesk.ticket.TicketRepository;
import com.nexadesk.user.User;
import com.nexadesk.user.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo profile only: 10 devices. Laptops and monitors go first to the employees who already reported a problem
 * with that kind of device, and those tickets are linked to their device; printing tickets are linked to the
 * matching shared printer. Idempotent (skipped if any equipment exists).
 */
@Slf4j
@Component
@Profile("demo")
@Order(6)
public class DemoEquipmentSeeder implements ApplicationRunner {

    private record Device(String name, EquipmentType type, String brand, String model, String serial,
                          int ageMonths, int warrantyMonths, String location) {
    }

    private static final Map<String, EquipmentType> SUBCATEGORY_TYPES = Map.of(
            "Ordinateur portable", EquipmentType.LAPTOP,
            "Poste fixe", EquipmentType.DESKTOP,
            "Écran", EquipmentType.MONITOR,
            "Périphériques (clavier, souris)", EquipmentType.KEYBOARD);

    private final EquipmentRepository equipmentRepository;
    private final EquipmentAssignmentRepository assignmentRepository;
    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final ZoneId zone;

    public DemoEquipmentSeeder(EquipmentRepository equipmentRepository,
                               EquipmentAssignmentRepository assignmentRepository, TicketRepository ticketRepository,
                               UserRepository userRepository, JdbcClient jdbc, Clock clock,
                               @Value("${nexadesk.time-zone}") ZoneId zone) {
        this.equipmentRepository = equipmentRepository;
        this.assignmentRepository = assignmentRepository;
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.jdbc = jdbc;
        this.clock = clock;
        this.zone = zone;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (equipmentRepository.count() > 0) {
            log.info("Equipment already present, skipping demo equipment");
            return;
        }
        List<Ticket> tickets = ticketRepository.findAll();
        List<User> laptopHolders = holders(tickets, "Ordinateur portable", 3,
                DemoAccounts.EMPLOYEE_EMAIL, "julien.martin@nexadesk.test", "hugo.lefebvre@nexadesk.test");
        List<User> monitorHolders = holders(tickets, "Écran", 2, DemoAccounts.EMPLOYEE_EMAIL, "lea.roux@nexadesk.test");
        User desktopHolder = holders(tickets, "Poste fixe", 1, "thomas.petit@nexadesk.test").getFirst();

        List<Equipment> laptops = List.of(
                device(new Device("Portable Dell Latitude", EquipmentType.LAPTOP, "Dell", "Latitude 5440", "DL5440-7KQ2",
                        14, 36, "Bureau"), laptopHolders.get(0), EquipmentStatus.IN_USE),
                device(new Device("Portable Lenovo ThinkPad", EquipmentType.LAPTOP, "Lenovo", "ThinkPad T14 Gen 4",
                        "PF4X9ZQ1", 26, 24, "Bureau"), laptopHolders.get(1), EquipmentStatus.IN_USE),
                device(new Device("Portable HP EliteBook", EquipmentType.LAPTOP, "HP", "EliteBook 840 G10", "5CG3291HTR",
                        8, 36, "Atelier informatique"), laptopHolders.get(2), EquipmentStatus.IN_REPAIR));
        Equipment desktop = device(new Device("Poste fixe Dell OptiPlex", EquipmentType.DESKTOP, "Dell", "OptiPlex 7010",
                "OP7010-3HV8", 40, 36, "Bureau"), desktopHolder, EquipmentStatus.IN_USE);
        List<Equipment> monitors = List.of(
                device(new Device("Écran Dell 24\"", EquipmentType.MONITOR, "Dell", "P2422H", "CN0D2W8F7", 14, 36,
                        "Bureau"), monitorHolders.get(0), EquipmentStatus.IN_USE),
                device(new Device("Écran LG 27\" 4K", EquipmentType.MONITOR, "LG", "27UL500-W", "209NTVS2K116", 30, 24,
                        "Bureau"), monitorHolders.get(1), EquipmentStatus.IN_USE));
        Equipment hrPrinter = device(new Device("Imprimante RH", EquipmentType.PRINTER, "HP", "LaserJet Enterprise M507",
                "PHBQK21409", 34, 24, "Ressources humaines – 1er étage"), null, EquipmentStatus.IN_USE);
        Equipment financePrinter = device(new Device("Copieur multifonction Finance", EquipmentType.PRINTER, "Canon",
                "imageRUNNER ADVANCE C3226i", "3RK02147", 20, 48, "Finance – 2e étage"), null, EquipmentStatus.IN_USE);
        device(new Device("Serveur de fichiers", EquipmentType.SERVER, "Dell", "PowerEdge R650", "JX4TQ93", 22, 60,
                "Salle serveur – sous-sol"), null, EquipmentStatus.IN_USE);
        device(new Device("Kit clavier et souris sans fil", EquipmentType.KEYBOARD, "Logitech", "MX Keys Combo",
                "2241LZ0PQ8", 3, 24, "Stock informatique"), null, EquipmentStatus.IN_STOCK);

        List<Equipment> personal = new ArrayList<>(laptops);
        personal.add(desktop);
        personal.addAll(monitors);
        int linked = linkTickets(tickets, personal, hrPrinter, financePrinter);
        log.info("Demo equipment created: 10 devices, {} tickets linked", linked);
    }

    /** Employees who reported a problem in this subcategory first, then the fallbacks, distinct. */
    private List<User> holders(List<Ticket> tickets, String subcategory, int count, String... fallbacks) {
        Set<User> holders = new LinkedHashSet<>();
        tickets.stream()
                .filter(t -> t.getSubcategory() != null && t.getSubcategory().getName().equals(subcategory))
                .map(Ticket::getRequester)
                .forEach(holders::add);
        for (String email : fallbacks) {
            userRepository.findByEmail(email).ifPresent(holders::add);
        }
        return holders.stream().limit(count).toList();
    }

    private Equipment device(Device d, User holder, EquipmentStatus status) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        long next = jdbc.sql("select nextval('equipment_code_seq')").query(Long.class).single();
        Equipment equipment = new Equipment("EQ-%04d".formatted(next), d.name(), d.type());
        equipment.setBrand(d.brand());
        equipment.setModel(d.model());
        equipment.setSerialNumber(d.serial());
        LocalDate acquired = today.minusMonths(d.ageMonths());
        equipment.setAcquisitionDate(acquired);
        equipment.setWarrantyEndDate(acquired.plusMonths(d.warrantyMonths()));
        equipment.setStatus(status);
        equipment.setAssignedUser(holder);
        equipment.setLocation(holder == null ? d.location()
                : holder.getDepartment() != null ? holder.getDepartment().getName() + " – " + d.location() : d.location());
        equipmentRepository.save(equipment);
        if (holder != null) {
            assignmentRepository.save(new EquipmentAssignment(equipment, holder,
                    acquired.atStartOfDay(zone).toInstant()));
        }
        return equipment;
    }

    private int linkTickets(List<Ticket> tickets, List<Equipment> personal, Equipment hrPrinter,
                            Equipment financePrinter) {
        int linked = 0;
        for (Ticket ticket : tickets) {
            Optional<Equipment> device = Optional.empty();
            String subcategory = ticket.getSubcategory() == null ? "" : ticket.getSubcategory().getName();
            EquipmentType type = SUBCATEGORY_TYPES.get(subcategory);
            if (type != null) {
                device = personal.stream()
                        .filter(e -> e.getType() == type && e.getAssignedUser() != null
                                && e.getAssignedUser().getId().equals(ticket.getRequester().getId()))
                        .findFirst();
            } else if (ticket.getCategory().getName().equals(DemoCategorySeeder.PRINTING)) {
                device = Optional.of(ticket.getTitle().contains("RH") ? hrPrinter : financePrinter);
            }
            if (device.isPresent()) {
                // Direct update: keeps the ticket's version and dates exactly as the demo story wrote them.
                jdbc.sql("update tickets set equipment_id = :equipment where id = :id")
                        .param("equipment", device.get().getId()).param("id", ticket.getId()).update();
                linked++;
            }
        }
        return linked;
    }
}
