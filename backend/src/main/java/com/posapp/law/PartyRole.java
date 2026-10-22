package com.posapp.law;

/** Who someone is on a matter, other than the client. Opposing party and opposing counsel are the other side for the conflict check; a co-client is on our side. */
public enum PartyRole {
    OPPOSING_PARTY, OPPOSING_COUNSEL, CO_CLIENT, WITNESS, EXPERT, JUDGE_OR_OFFICIAL, RELATED, OTHER
}
