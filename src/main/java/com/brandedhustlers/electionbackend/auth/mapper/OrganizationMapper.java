package com.brandedhustlers.electionbackend.auth.mapper;

import com.brandedhustlers.electionbackend.auth.domain.Organization;
import com.brandedhustlers.electionbackend.auth.dto.OrganizationDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrganizationMapper {

    OrganizationDTO entityToDTO(Organization organization);

    Organization dtoToEntity(OrganizationDTO organizationDTO);
}
