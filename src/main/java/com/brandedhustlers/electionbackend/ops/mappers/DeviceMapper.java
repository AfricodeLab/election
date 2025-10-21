package com.brandedhustlers.electionbackend.ops.mappers;

import com.brandedhustlers.electionbackend.ops.domain.Device;
import com.brandedhustlers.electionbackend.ops.dto.DeviceDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DeviceMapper{

    DeviceDto entityToDto(Device device);

    Device dtoToEntity(DeviceDto dto);
}