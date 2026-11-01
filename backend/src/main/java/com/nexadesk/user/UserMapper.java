package com.nexadesk.user;

import com.nexadesk.user.dto.CurrentUserDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface UserMapper {

    @Mapping(target = "role", source = "role.code")
    @Mapping(target = "department", source = "department.name")
    CurrentUserDto toCurrentUser(User user);
}
