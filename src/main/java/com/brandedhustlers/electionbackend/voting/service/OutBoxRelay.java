package com.brandedhustlers.electionbackend.voting.service;

public interface OutBoxRelay {
    void publishPending();
}
