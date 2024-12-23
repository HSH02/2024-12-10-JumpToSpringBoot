package com.mysite.sbb.service.impl;


import com.mysite.sbb.domain.dto.QuestionDetailResponseDTO;
import com.mysite.sbb.domain.dto.QuestionListResponseDTO;
import com.mysite.sbb.domain.dto.QuestionRequestDTO;
import com.mysite.sbb.domain.entity.Answer;
import com.mysite.sbb.domain.entity.Question;
import com.mysite.sbb.domain.entity.SiteUser;
import com.mysite.sbb.domain.repository.AnswerRepository;
import com.mysite.sbb.domain.repository.QuestionRepository;
import com.mysite.sbb.global.exception.DataNotFoundException;
import com.mysite.sbb.service.QuestionService;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class QuestionServiceImpl implements QuestionService {

    private final QuestionRepository questionRepository;
    private final UserServiceImpl userServiceImpl;
    private final AnswerRepository answerRepository;

    @Override
    public List<QuestionListResponseDTO> getAllQuestions() {

        List<Question> all = questionRepository.findAll();

        return all.stream()
                .map(QuestionListResponseDTO::new)
                .collect(Collectors.toList());
    }

    @Override
    public Page<QuestionListResponseDTO> getList(int page, String kw) {
        // 1. 정렬 조건 설정
        Pageable pageable = PageRequest.of(page, 10, Sort.by(Sort.Order.desc("createDate")));

        // 2. 검색 조건 설정
        Specification<Question> spec = search(kw);

        // 3. 엔티티 -> DTO 변환
        Page<Question> questions = questionRepository.findAll(spec, pageable);// 검색 및 페이징 결과
        return questions.map(QuestionListResponseDTO::new);
    }

    @Override
    public QuestionDetailResponseDTO getQuestionDetail(Integer id, int page, String sortKeyword) {
        // 1. 질문 조회
        Question question = questionRepository.findById(id).orElseThrow(() -> new DataNotFoundException("question not found"));

        // 2. 답변 페이징
        Pageable pageable = PageRequest.of(page, 5, Sort.by(Sort.Order.desc(sortKeyword)));
        Page<Answer> answers = answerRepository.findByQuestion(question, pageable);

        // 3. DTO 변환
        return new QuestionDetailResponseDTO(question, answers);
    }

    @Override
    public Question getQuestion(Integer id) {
        return questionRepository.findById(id).orElseThrow(() -> new DataNotFoundException("question not found"));
    }

    @Override
    public void create(QuestionRequestDTO questionRequestDTO, String userName) {
        SiteUser user = userServiceImpl.getUser(userName);
        Question question = new Question();
        question.setSubject(questionRequestDTO.getSubject());
        question.setContent(questionRequestDTO.getContent());
        question.setCreateDate(LocalDateTime.now());
        question.setAuthor(user);
        questionRepository.save(question);
    }

    @Override
    public void modify(Integer id, QuestionRequestDTO questionRequestDTO, String userName) {
        Question question = getQuestion(id);

        if (!question.getAuthor().getUsername().equals(userName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "수정 권한이 없습니다.");
        }

        question.setSubject(questionRequestDTO.getSubject());
        question.setContent(questionRequestDTO.getContent());
        question.setModifyDate(LocalDateTime.now());
        questionRepository.save(question);
    }

    @Override
    public void delete(Integer id, String userName) {
        Question question = getQuestion(id);

        if (!question.getAuthor().getUsername().equals(userName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "삭제 권한이 없습니다.");
        }

        this.questionRepository.delete(question);
    }

    @Override
    public void vote(Integer id, String userName) {
        Question question = getQuestion(id);
        SiteUser siteUser = userServiceImpl.getUser(userName);
        question.getVoter().add(siteUser);
        this.questionRepository.save(question);
    }

    @Override
    public Specification<Question> search(String kw) {
        return new Specification<>() {
            @Serial
            private static final long serialVersionUID = 1L;

            @Override
            public Predicate toPredicate(Root<Question> q, CriteriaQuery<?> query, CriteriaBuilder cb) {
                query.distinct(true);
                Join<Question, SiteUser> u1 = q.join("author", JoinType.LEFT);
                Join<Question, Answer> a = q.join("answerList", JoinType.LEFT);
                Join<Question, SiteUser> u2 = q.join("author", JoinType.LEFT);
                return cb.or(cb.like(q.get("subject"), "%" + kw + "%"),
                        cb.like(q.get("content"), "%" + kw + "%"),
                        cb.like(u1.get("username"), "%" + kw + "%"),
                        cb.like(a.get("content"), "%" + kw + "%"),
                        cb.like(u2.get("username"), "%" + kw + "%"));
            }
        };
    }
}
