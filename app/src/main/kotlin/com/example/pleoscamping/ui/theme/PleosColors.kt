package com.example.pleoscamping.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Pleos 시스템 UI 색 토큰 (SPEC_DARK 1절).
 * 다크 고정이며 라이트 테마는 없다. 모든 화면은 이 토큰만 쓴다.
 */
val PleosBg = Color(0xFF000000) // 화면 바탕 (Pleos 설정·서랍과 같은 값)
val PleosCard = Color(0xFF14151B) // 카드·패널·상태 띠·다이얼로그
val PleosRaised = Color(0xFF1E2029) // 보조 버튼, 정보 칸, 큰 숫자 상자, 시간표 칸
val PleosLine = Color(0xFF2A2D38) // 1dp 테두리, 꺼진 토글 트랙, 슬라이더 비활성
val PleosFg = Color(0xFFF1F2F6) // 본문 글자, 아이콘 선
val PleosMuted = Color(0xFF8D91A0) // 보조 글자(설명, 단위, 안내)
val PleosAccent = Color(0xFF9AA8FF) // 켜진 카드 테두리·아이콘, 강조 글자·선 (달빛 인디고)
val PleosAccentSolid = Color(0xFF7C8BFF) // 주 버튼 채움, 켜진 토글 트랙, 슬라이더 활성
val PleosAccentSoft = Color(0x1F9AA8FF) // 켜진 카드 위쪽 은은한 바탕, 선택 항목 바탕
val PleosOnAccent = Color(0xFF0B0E1E) // 주 버튼 글자, 켜진 토글 손잡이, 단계 배지 글자
val PleosOn = Color(0xFF9AA8FF) // "차량 연결됨" 점, 켜짐 상태 (포인트 색과 같게)
val PleosOk = Color(0xFF6FD3B4) // 배터리 가드 정상, 마이크 레벨 낮음
val PleosSave = Color(0xFFE7C15B) // 배터리 가드 절약
val PleosWarn = Color(0xFFF0A35B) // 배터리 가드 경계, 주행 중 상태 띠
val PleosDanger = Color(0xFFF0696B) // 배터리 가드 위험, VC 실패 점, 마이크 레벨 높음
val PleosButtonAlt = Color(0xFF1E2029) // (PleosRaised와 같은 값, 기존 참조 호환)
