package com.nexadesk.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.nexadesk.audit.AuditService;
import com.nexadesk.category.CategoryMapperImpl;
import com.nexadesk.category.TicketCategory;
import com.nexadesk.category.TicketCategoryRepository;
import com.nexadesk.category.dto.CategoryDtos.CategoryRefDto;
import com.nexadesk.exception.ErrorCode;
import com.nexadesk.support.TestUsers;
import com.nexadesk.ticket.TicketRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TechnicianServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TicketCategoryRepository categoryRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private AuditService audit;

    private TechnicianService service;

    @BeforeEach
    void setUp() {
        service = new TechnicianService(userRepository, categoryRepository, ticketRepository, new CategoryMapperImpl(),
                audit);
    }

    private static TicketCategory category(long id, String name, boolean active) {
        TicketCategory category = BeanUtils.instantiateClass(TicketCategory.class);
        ReflectionTestUtils.setField(category, "id", id);
        category.setName(name);
        category.setIcon("tag");
        category.setActive(active);
        return category;
    }

    private User technician() {
        User user = TestUsers.user(5L, "tech@nexadesk.test", RoleCode.TECHNICIAN, "hash");
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void specialtiesAreReplacedAndReturnedSortedByName() {
        technician();
        when(categoryRepository.findAllByIdIn(List.of(2L, 1L)))
                .thenReturn(List.of(category(2, "Réseau & VPN", true), category(1, "Matériel", true)));

        List<CategoryRefDto> result = service.replaceSpecialties(5L, List.of(2L, 1L, 2L));

        assertThat(result).extracting(CategoryRefDto::name).containsExactly("Matériel", "Réseau & VPN");
    }

    @Test
    void onlyTechniciansCanHaveSpecialties() {
        User employee = TestUsers.user(6L, "emp@nexadesk.test", RoleCode.EMPLOYEE, "hash");
        when(userRepository.findById(6L)).thenReturn(Optional.of(employee));

        assertThatThrownBy(() -> service.replaceSpecialties(6L, List.of(1L)))
                .extracting("code").isEqualTo(ErrorCode.SPECIALTIES_TECHNICIAN_ONLY);
    }

    @Test
    void unknownCategoryIsReported() {
        technician();
        when(categoryRepository.findAllByIdIn(List.of(1L, 42L))).thenReturn(List.of(category(1, "Matériel", true)));

        assertThatThrownBy(() -> service.replaceSpecialties(5L, List.of(1L, 42L)))
                .hasMessageContaining("42")
                .extracting("code").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void deactivatedCategoryCannotBeNewlyAssignedButAnExistingOneIsKept() {
        User user = technician();
        TicketCategory fax = category(3, "Fax", false);
        when(categoryRepository.findAllByIdIn(List.of(3L))).thenReturn(List.of(fax));

        assertThatThrownBy(() -> service.replaceSpecialties(5L, List.of(3L)))
                .extracting("code").isEqualTo(ErrorCode.INACTIVE_REFERENCE);

        user.replaceSpecialties(List.of(fax));
        assertThat(service.replaceSpecialties(5L, List.of(3L))).hasSize(1);
    }

    @Test
    void emptyListRemovesAllSpecialties() {
        User user = technician();
        user.replaceSpecialties(List.of(category(1, "Matériel", true)));
        when(categoryRepository.findAllByIdIn(List.of())).thenReturn(List.of());

        assertThat(service.replaceSpecialties(5L, List.of())).isEmpty();
        assertThat(user.getSpecialties()).isEmpty();
    }
}
