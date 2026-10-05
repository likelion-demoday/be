package com.example.resay.domain.character.service;

public class CharacterImageDeletionInProgressException extends RuntimeException {

    public CharacterImageDeletionInProgressException() {
        super("생성 중인 캐릭터 이미지는 삭제할 수 없습니다.");
    }
}
