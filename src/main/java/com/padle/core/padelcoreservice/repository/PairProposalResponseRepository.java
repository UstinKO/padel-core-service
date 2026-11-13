package com.padle.core.padelcoreservice.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PairProposalResponseRepository {
    public enum Response { DECLINED, ACCEPTED, PAIR_FORMED, EXPIRED }

    private final JdbcClient jdbc;

    public Optional<Response> find(String token) {
        return jdbc.sql("SELECT response FROM pair_proposal_responses_db WHERE token = :token")
                .param("token", token)
                .query(String.class).optional().map(Response::valueOf);
    }

    public void record(String token, Response response) {
        if (token == null || !token.startsWith("PP_")) return;
        jdbc.sql("""
                INSERT INTO pair_proposal_responses_db(token, response)
                VALUES (:token, :response) ON CONFLICT (token) DO NOTHING
                """).param("token", token).param("response", response.name()).update();
    }
}
