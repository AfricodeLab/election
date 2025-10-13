package com.brandedhustlers.electionbackend.election.mapper;

import com.brandedhustlers.electionbackend.election.domain.Candidate;
import com.brandedhustlers.electionbackend.election.dto.CandidateDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CandidateMapper {

    CandidateDTO entityToDTO(Candidate candidate);

    Candidate dtoToEntity(CandidateDTO candidateDTO);
}
