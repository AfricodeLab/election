package com.brandedhustlers.electionbackend.auth.mapper;

import com.brandedhustlers.electionbackend.auth.domain.AppUser;
import com.brandedhustlers.electionbackend.auth.dto.AppUserDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AppUserMapper {

    AppUserDTO entityToDTO(AppUser appUser);

    AppUser dtoToEntity(AppUserDTO appUserDTO);
}
