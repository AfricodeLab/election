package com.brandedhustlers.electionbackend.common.config.messaging;

public class RabbitTopics {

    private RabbitTopics() {
    }

    public static final String VOTER_IMPORT = "voter.import";
    public static final String NOTIF_SMS = "notif.sms";
    public static final String VOTING_VOTE_CAST = "voting.vote.cast";
}
