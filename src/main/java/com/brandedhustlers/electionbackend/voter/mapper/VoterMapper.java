package com.brandedhustlers.electionbackend.voter.mapper;

import com.brandedhustlers.electionbackend.voter.domain.Voter;
import com.brandedhustlers.electionbackend.voter.dto.VoterDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface VoterMapper {

    VoterDTO entityToDto(Voter voter);

    Voter dtoToEntity(VoterDTO voterDTO);
}
