package com.nexadesk.equipment;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexadesk.TestcontainersConfiguration;
import com.nexadesk.category.TicketCategory;
import com.nexadesk.category.TicketCategoryRepository;
import com.nexadesk.user.RoleCode;
import com.nexadesk.user.RoleRepository;
import com.nexadesk.user.User;
import com.nexadesk.user.UserRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EquipmentIntegrationTest {

    private static final String PASSWORD = "Integration@2026";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private TicketCategoryRepository categoryRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private String run;
    private long categoryId;
    private long employeeId;
    private long otherEmployeeId;
    private String employee;
    private String otherEmployee;
    private String technician;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        run = UUID.randomUUID().toString().substring(0, 8);
        TicketCategory category = categoryRepository.save(new TicketCategory("Equip " + run, null, "tag", null));
        categoryId = category.getId();
        employeeId = user("emp." + run, RoleCode.EMPLOYEE, null).getId();
        otherEmployeeId = user("emp2." + run, RoleCode.EMPLOYEE, null).getId();
        user("tech." + run, RoleCode.TECHNICIAN, category);
        user("admin." + run, RoleCode.ADMIN, null);
        employee = token("emp." + run);
        otherEmployee = token("emp2." + run);
        technician = token("tech." + run);
        admin = token("admin." + run);
    }

    private User user(String login, RoleCode role, TicketCategory specialty) {
        User user = User.builder().email(login + "@nexadesk.test").passwordHash(passwordEncoder.encode(PASSWORD))
                .firstName("IT").lastName(login).role(roleRepository.findByCode(role).orElseThrow()).build();
        if (specialty != null) {
            user.replaceSpecialties(List.of(specialty));
        }
        return userRepository.save(user);
    }

    private String token(String login) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", login + "@nexadesk.test",
                                "password", PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asText();
    }

    private ResultActions call(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions call(String token, MockHttpServletRequestBuilder request, Object body) throws Exception {
        return call(token, request.contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private long createDevice(String name, String type, String serial, String location) throws Exception {
        Map<String, Object> request = new HashMap<>(Map.of("name", name, "type", type, "brand", "Dell",
                "acquisitionDate", LocalDate.now().minusYears(1).toString(),
                "warrantyEndDate", LocalDate.now().plusYears(1).toString()));
        if (serial != null) {
            request.put("serialNumber", serial);
        }
        if (location != null) {
            request.put("location", location);
        }
        return body(call(admin, post("/api/equipment"), request).andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    void adminRegistersDevicesWithGeneratedCodesAndUniqueSerials() throws Exception {
        String serial = "sn-" + run;
        long id = createDevice("Portable " + run, "LAPTOP", serial, null);

        call(admin, get("/api/equipment/{id}", id))
                .andExpect(jsonPath("$.inventoryCode", matchesPattern("EQ-\\d{4,}")))
                .andExpect(jsonPath("$.serialNumber").value(serial.toUpperCase()))
                .andExpect(jsonPath("$.status").value("IN_STOCK"))
                .andExpect(jsonPath("$.underWarranty").value(true));

        call(admin, post("/api/equipment"), Map.of("name", "Doublon", "type", "LAPTOP", "serialNumber", serial))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SERIAL_NUMBER_ALREADY_USED"));
        call(admin, post("/api/equipment"), Map.of("name", "Garantie incohérente", "type", "MONITOR",
                        "acquisitionDate", "2026-01-10", "warrantyEndDate", "2025-01-10"))
                .andExpect(status().isBadRequest());
        call(technician, post("/api/equipment"), Map.of("name", "Interdit", "type", "MOUSE"))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignmentLifecycleIsTrackedAndEnforced() throws Exception {
        long id = createDevice("Portable " + run, "LAPTOP", null, null);

        call(admin, post("/api/equipment/{id}/assign", id), Map.of("userId", employeeId))
                .andExpect(jsonPath("$.status").value("IN_USE"))
                .andExpect(jsonPath("$.assignedUser.id").value(employeeId));
        call(admin, post("/api/equipment/{id}/assign", id), Map.of("userId", otherEmployeeId))
                .andExpect(jsonPath("$.assignedUser.id").value(otherEmployeeId));

        // In repair: keeps its holder, cannot be reassigned.
        call(technician, put("/api/equipment/{id}/status", id), Map.of("status", "IN_REPAIR"))
                .andExpect(jsonPath("$.status").value("IN_REPAIR"))
                .andExpect(jsonPath("$.assignedUser.id").value(otherEmployeeId));
        call(admin, post("/api/equipment/{id}/assign", id), Map.of("userId", employeeId))
                .andExpect(status().isConflict());
        call(technician, put("/api/equipment/{id}/status", id), Map.of("status", "IN_STOCK"))
                .andExpect(status().isConflict());
        call(technician, put("/api/equipment/{id}/status", id), Map.of("status", "IN_USE")).andExpect(status().isOk());

        Map<String, Object> unassign = new HashMap<>();
        unassign.put("userId", null);
        call(admin, post("/api/equipment/{id}/assign", id), unassign)
                .andExpect(jsonPath("$.status").value("IN_STOCK"))
                .andExpect(jsonPath("$.assignedUser").isEmpty());

        call(technician, get("/api/equipment/{id}/assignments", id))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].user.id").value(otherEmployeeId))
                .andExpect(jsonPath("$[0].returnedAt").isNotEmpty())
                .andExpect(jsonPath("$[1].user.id").value(employeeId));

        call(admin, post("/api/equipment/{id}/retire", id)).andExpect(jsonPath("$.status").value("RETIRED"));
        call(admin, put("/api/equipment/{id}/status", id), Map.of("status", "IN_USE"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_RETIRED"));
    }

    @Test
    void sharedDevicesNeedALocation() throws Exception {
        long withoutLocation = createDevice("Imprimante " + run, "PRINTER", null, null);
        call(admin, put("/api/equipment/{id}/status", withoutLocation), Map.of("status", "IN_USE"))
                .andExpect(status().isBadRequest());

        long printer = createDevice("Imprimante RH " + run, "PRINTER", null, "RH – 1er étage");
        call(admin, put("/api/equipment/{id}/status", printer), Map.of("status", "IN_USE"))
                .andExpect(jsonPath("$.shared").value(true));
    }

    @Test
    void employeesOnlySeeAndLinkTheirOwnOrSharedDevices() throws Exception {
        long mine = createDevice("Mon portable " + run, "LAPTOP", null, null);
        long theirs = createDevice("Son portable " + run, "LAPTOP", null, null);
        long shared = createDevice("Copieur " + run, "PRINTER", null, "Accueil");
        call(admin, post("/api/equipment/{id}/assign", mine), Map.of("userId", employeeId));
        call(admin, post("/api/equipment/{id}/assign", theirs), Map.of("userId", otherEmployeeId));
        call(admin, put("/api/equipment/{id}/status", shared), Map.of("status", "IN_USE"));

        call(employee, get("/api/equipment"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(mine));
        call(employee, get("/api/equipment/{id}", theirs)).andExpect(status().isNotFound());
        String selectable = call(employee, get("/api/equipment/selectable")).andReturn().getResponse()
                .getContentAsString();
        org.assertj.core.api.Assertions.assertThat(selectable)
                .contains("\"id\":" + mine, "\"id\":" + shared).doesNotContain("\"id\":" + theirs + ",");

        Map<String, Object> ticket = new HashMap<>(Map.of("type", "INCIDENT", "title", "Portable très lent",
                "description", "Le portable met cinq minutes à démarrer.", "categoryId", categoryId,
                "impact", "LOW", "urgency", "MEDIUM"));
        ticket.put("equipmentId", theirs);
        call(employee, post("/api/tickets"), ticket)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EQUIPMENT_NOT_SELECTABLE"));

        ticket.put("equipmentId", mine);
        long ticketId = body(call(employee, post("/api/tickets"), ticket)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.equipment.id").value(mine))).get("id").asLong();

        // Incident history of the device, for the IT team.
        call(technician, get("/api/equipment/{id}/tickets", mine))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ticketId));
        call(employee, get("/api/equipment/{id}/tickets", mine)).andExpect(status().isForbidden());

        // Unlinking through the edit endpoint is traced in the history.
        call(employee, put("/api/tickets/{id}", ticketId), Map.of("title", "Portable très lent",
                "description", "Le portable met cinq minutes à démarrer.", "version", 0))
                .andExpect(jsonPath("$.equipment").isEmpty());
        call(employee, get("/api/tickets/{id}/history", ticketId))
                .andExpect(jsonPath("$[?(@.field == 'equipment')]").isNotEmpty());
    }
}
