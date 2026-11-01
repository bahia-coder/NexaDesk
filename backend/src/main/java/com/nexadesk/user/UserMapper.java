package com.nexadesk.user;

import com.nexadesk.user.dto.CurrentUserDto;
import com.nexadesk.user.dto.DepartmentDto;
import com.nexadesk.user.dto.ProfileDto;
import com.nexadesk.user.dto.UserDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface UserMapper {

    @Mapping(target = "role", source = "role.code")
    @Mapping(target = "department", source = "department.name")
    CurrentUserDto toCurrentUser(User user);

    @Mapping(target = "role", source = "role.code")
    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "departmentName", source = "department.name")
    UserDto toDto(User user);

    @Mapping(target = "role", source = "role.code")
    @Mapping(target = "department", source = "department.name")
    ProfileDto toProfile(User user);

    DepartmentDto toDto(Department department);
}
