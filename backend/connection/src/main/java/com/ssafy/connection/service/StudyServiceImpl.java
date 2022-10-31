package com.ssafy.connection.service;

import com.ssafy.connection.dto.ConnStudyDto;
import com.ssafy.connection.dto.StudyDto;
import com.ssafy.connection.entity.ConnStudy;
import com.ssafy.connection.entity.Study;
import com.ssafy.connection.repository.ConnStudyRepository;
import com.ssafy.connection.repository.StudyRepository;
import com.ssafy.connection.securityOauth.domain.entity.user.User;
import com.ssafy.connection.securityOauth.repository.user.UserRepository;
import com.ssafy.connection.util.RandomCodeGenerate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

@Service
public class StudyServiceImpl implements StudyService {

    private final String githubToken = "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";
    private WebClient webClient = WebClient.create("https://api.github.com");

    private final UserRepository userRepository;
    private final StudyRepository studyRepository;
    private final ConnStudyRepository connStudyRepository;

    public StudyServiceImpl(UserRepository userRepository, StudyRepository studyRepository, ConnStudyRepository connStudyRepository) {
        this.userRepository = userRepository;
        this.studyRepository = studyRepository;
        this.connStudyRepository = connStudyRepository;
    }

    @Override
    @Transactional
    public void createStudy(long userId, StudyDto studyDto) {
        try {
            User userEntity = userRepository.findById(userId).get();
            String studyName = studyDto.getStudyName();
            String studyCode = null;

            do {
                studyCode = RandomCodeGenerate.generate();
            } while (studyRepository.findByStudyCode(studyCode) == null);

            System.out.println("1111111111");

            String createTeamRequest = "{\"name\":\"" + userEntity.getGithubId() + "\"," +
                    "\"description\":\"This is your study repository\"," +
                    "\"permission\":\"push\"," +
                    "\"privacy\":\"closed\"}";

            webClient.post()
                    .uri("/orgs/{org}/teams", "co-nnection")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken)
                    .bodyValue(createTeamRequest)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            String inviteUserRequest = "{\"role\":\"maintainer\"}";

            webClient.put()
                    .uri("/orgs/{org}/teams/{team_slug}/memberships/{username}", "co-nnection",userEntity.getGithubId(), userEntity.getGithubId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken)
                    .bodyValue(inviteUserRequest)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            System.out.println("22222222222");

            String createRepositoryRequest = "{\"name\":\"" + userEntity.getGithubId() + "\"," +
                    "\"description\":\"This is your Study repository\"," +
                    "\"homepage\":\"https://github.com\"," +
                    "\"private\":false," +
                    "\"has_issues\":true," +
                    "\"has_projects\":true," +
                    "\"has_wiki\":true}";

            webClient.post()
                    .uri("/orgs/{org}/repos", "co-nnection")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken)
                    .bodyValue(createRepositoryRequest)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            System.out.println("3333333333");

            String connectTeamRepositoryRequest = "{\"permission\":\"push\"}";

            webClient.put()
                    .uri("/orgs/{org}/teams/{team_slug}/repos/{owner}/{repo}","co-nnection",userEntity.getGithubId(), "co-nnection",userEntity.getGithubId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken)
                    .bodyValue(connectTeamRepositoryRequest)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            System.out.println("44444444");

            Study study = new Study();
            study.setStudyCode(studyCode);
            study.setStudyName(studyName);
            study.setStudyRepository("https://github.com/co-nnection/" + userEntity.getGithubId());
            study.setStudyPersonnel(1);
            studyRepository.save(study);

            Study studyEntity = studyRepository.findByStudyCode(studyCode).get();

            ConnStudy connStudy = new ConnStudy();
            connStudy.setRole("LEADER");
            connStudy.setStudy(studyEntity);
            connStudy.setUser(userEntity);
            connStudyRepository.save(connStudy);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional
    public StudyDto getStudy(String studyCode) {
        Study studyEntity = studyRepository.findByStudyCode(studyCode).get();
        StudyDto studyDto = StudyDto.of(studyEntity);

        return studyDto;
    }

    @Override
    @Transactional
    public void joinStudy(long userId, String studyCode) {
        try {
            User userEntity = userRepository.findById(userId).get();
            Study studyEntity = studyRepository.findByStudyCode(studyCode).get();
            ConnStudy connStudyEntity = connStudyRepository.findByStudy_StudyId(studyEntity.getStudyId()).get();
            User studyLeaderEntity = connStudyEntity.getUser();
            String inviteUserRequest = "{\"role\":\"maintainer\"}";

            webClient.put()
                    .uri("/orgs/{org}/teams/{team_slug}/memberships/{username}", "co-nnection", studyLeaderEntity.getGithubId(), userEntity.getGithubId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken)
                    .bodyValue(inviteUserRequest)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            ConnStudy connStudy = new ConnStudy();
            connStudy.setRole("MEMBER");
            connStudy.setStudy(studyEntity);
            connStudy.setUser(userEntity);
            connStudyRepository.save(connStudy);
            studyEntity.setStudyPersonnel(studyEntity.getStudyPersonnel()+1);
            studyRepository.save(studyEntity);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional
    public void quitStudy(long userId, Long quitUserId) {
        try {
            User userEntity = userRepository.findById(userId).get(); // quitUserId 없을 때, 스터디장인지 여부 확인 추가 구현
            User quitUserEntity = null;
            ConnStudy connStudyEntity = null;
            Study studyEntity = null;
            
            if (quitUserId == null) {
                quitUserEntity = userRepository.findById(userId).get();
                connStudyEntity = connStudyRepository.findByUser_UserId(userId).get();
            } else {
                quitUserEntity = userRepository.findById(quitUserId).get();
                connStudyEntity = connStudyRepository.findByUser_UserId(quitUserId).get();
            }
            
            studyEntity = studyRepository.findById(connStudyEntity.getStudy().getStudyId()).get();

            webClient.delete()
                    .uri("/orgs/{org}/teams/{team_slug}/memberships/{username}", "co-nnection", studyEntity.getStudyRepository().substring(31), quitUserEntity.getGithubId())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block();

            studyEntity.setStudyPersonnel(studyEntity.getStudyPersonnel()-1);
            connStudyRepository.delete(connStudyEntity);
            studyRepository.save(studyEntity);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteStudy(long userId) {
        User userEntity = userRepository.findById(userId).get();
        // 스터디장일때만 삭제 처리 추가
        webClient.delete()
                .uri("/orgs/{org}/teams/{team_slug}", "co-nnection", userEntity.getGithubId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + githubToken)
                .retrieve()
                .bodyToMono(Void.class)
                .block();

        ConnStudy connStudyEntity = connStudyRepository.findByUser_UserId(userId).get();
        Study studyEntity = studyRepository.findById(connStudyEntity.getStudy().getStudyId()).get();
        List<ConnStudy> connStudyList = connStudyRepository.findAllByStudy_StudyId(connStudyEntity.getStudy().getStudyId());

        for (ConnStudy connStudy : connStudyList) {
            connStudyRepository.delete(connStudy);
        }
        studyRepository.delete(studyEntity);
    }

    @Override
    @Transactional
    public int getStudyTier(Long userId) {
        ConnStudy connStudy = connStudyRepository.findByUser_UserId(userId).get();
        List<ConnStudy> connStudyList = connStudyRepository.findAllByStudy_StudyId(connStudy.getStudy().getStudyId());

        int avgTier = 0;
        for(ConnStudy temp : connStudyList){
            avgTier += temp.getUser().getTier();
        }
        avgTier = Math.round(avgTier / connStudyList.size());
        return avgTier;
    }
}
