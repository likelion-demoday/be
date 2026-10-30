package com.example.resay.domain.character.service;

public class CharacterImageProcessingException extends RuntimeException {

    public CharacterImageProcessingException() {
        super("캐릭터 이미지 일부 또는 전체 생성에 실패했습니다.");
    }
}
