package com.brandedhustlers.electionbackend.ops.mappers;

import com.brandedhustlers.electionbackend.ops.domain.Launch;
import com.brandedhustlers.electionbackend.ops.dto.LaunchDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LaunchMapper {

    LaunchDto entityToDto(Launch launch);

    Launch dtoToEntity(LaunchDto launchDto);
}
