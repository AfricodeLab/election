package com.brandedhustlers.electionbackend.ops.mappers;

import com.brandedhustlers.electionbackend.ops.domain.Station;
import com.brandedhustlers.electionbackend.ops.dto.StationDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface StationMapper {

    StationDto entityToDto(Station station);

    Station dtoToEntity(StationDto dto);
}
