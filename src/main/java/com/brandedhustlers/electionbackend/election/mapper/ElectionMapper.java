package com.brandedhustlers.electionbackend.election.mapper;

import com.brandedhustlers.electionbackend.election.domain.Election;
import com.brandedhustlers.electionbackend.election.dto.ElectionDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ElectionMapper {

    ElectionDTO entityToDto(Election election);

    Election dtoToEntity(ElectionDTO electionDTO);
}
