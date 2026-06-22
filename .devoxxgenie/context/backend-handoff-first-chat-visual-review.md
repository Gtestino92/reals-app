Se ajustó el contrato backend para cerrar mejor el flujo first-chat -> approve/reject -> visual-phase. El objetivo es que el front deje de inferir datos críticos y pueda renderizar partner, decisiones y polling incremental.

Cambios disponibles:

1. Home ahora trae partner y expiración en chats activos

Endpoint:
GET /api/me/home

En activeMatches[].firstChat, mientras el match esté en CHAT_ACTIVE:

{
"chatId": "...",
"chatType": "FIRST_CHAT",
"chatStatus": "ACTIVE",
"expiresAt": "...",
"partner": {
"userId": "...",
"profileId": "...",
"displayName": "Ana"
}
}

Notas:
- Si el match pasa a VISUAL_PHASE, sigue apareciendo en activeMatches[] con matchState = "VISUAL_PHASE", pero firstChat viene null.
- Esto permite que el front deje de mostrar "Partner" y use firstChat.partner.displayName.
- Cuando detecten VISUAL_PHASE, deben salir del chat y mostrar el flujo de visual approval.

2. GET del first chat ahora trae contrato enriquecido

Endpoint:
GET /api/matches/{matchId}/chat

Respuesta incluye los campos previos del chat más:

{
"expiresAt": "...",
"partner": {
"userId": "...",
"profileId": "...",
"displayName": "Ana"
},
"myDecision": "PENDING | APPROVED | REJECTED | ABANDONED",
"partnerDecision": "PENDING | APPROVED | REJECTED | ABANDONED"
}

Uso esperado:
- Si myDecision != PENDING, deshabilitar botones de approve/reject.
- Si myDecision = APPROVED y partnerDecision = PENDING, mostrar estado tipo “esperando decisión del partner”.
- Si ambos aprueban, el POST de decisión devuelve match.state = VISUAL_PHASE.

3. Semántica oficial de chat-decision

Endpoint:
POST /api/matches/{matchId}/chat-decision

Body:
{
"decision": "APPROVED | REJECTED"
}

Semántica:
- APPROVED es individual. Requiere aprobación de ambos usuarios.
- Cuando ambos aprueban, backend mueve el match a VISUAL_PHASE.
- REJECTED NO es consensuado. Es cancelación unilateral:
    - cierra el first chat
    - mueve match a CHAT_REJECTED
    - libera locks
    - aplica política de penalización si corresponde

Para salida consensuada no usar chat-decision. Usar endpoints existentes por chatId:
POST /api/chats/{chatId}/exit-requests
GET /api/chats/{chatId}/exit-requests
POST /api/chats/{chatId}/exit-requests/{exitRequestId}/acceptance
POST /api/chats/{chatId}/exit-requests/{exitRequestId}/rejection

4. Safety report actual

Endpoint existente:
POST /api/chats/{chatId}/safety-cancellations

Body:
{
"reason": "NO_LONGER_INTERESTED | INAPPROPRIATE_BEHAVIOR | HARASSMENT | OTHER",
"details": "..."
}

Semántica:
- Cierra el chat.
- Exime al reporter.
- Penaliza al usuario reportado.

Todavía NO hay alias por matchId tipo POST /api/matches/{matchId}/safety-report.

5. Polling incremental de mensajes

Endpoint:
GET /api/chats/{chatId}/messages

Modo legacy, sin cursor:
Devuelve array:
[
{
"id": "...",
"chatSessionId": "...",
"senderId": "...",
"content": "...",
"sentAt": "..."
}
]

Modo incremental:
GET /api/chats/{chatId}/messages?after={messageId}

También acepta:
GET /api/chats/{chatId}/messages?afterMessageId={messageId}

Respuesta:
{
"messages": [
{
"id": "...",
"chatSessionId": "...",
"senderId": "...",
"content": "...",
"sentAt": "..."
}
],
"hasMore": false,
"serverTime": "..."
}

Uso esperado:
- Guardar el último message.id recibido.
- En polling, llamar con ?after=lastMessageId.
- Append de response.messages.
- hasMore por ahora siempre false.

6. Flujo recomendado en front

Home:
- GET /api/me/home
- Si activeMatches[].matchState == CHAT_ACTIVE y firstChat != null:
    - mostrar card de first chat
    - usar firstChat.partner.displayName
    - usar firstChat.expiresAt para countdown
- Si activeMatches[].matchState == VISUAL_PHASE:
    - no esperar firstChat
    - mostrar entrada a visual approval

Chat screen:
- Al entrar: GET /api/matches/{matchId}/chat
- Renderizar partner.displayName
- Renderizar countdown con expiresAt
- Renderizar estado con myDecision / partnerDecision
- Para mensajes:
    - primera carga puede usar GET /api/chats/{chatId}/messages sin cursor
    - polling posterior usar GET /api/chats/{chatId}/messages?after={lastMessageId}

Approve:
- POST /api/matches/{matchId}/chat-decision { "decision": "APPROVED" }
- Si response.state == CHAT_ACTIVE: salir del chat y volver a busqueda. El match aprobado unilateralmente no deberia aparecer como experiencia activa para ese usuario hasta que el partner tambien apruebe.
- Si response.state == VISUAL_PHASE: volver a Home o navegar a visual approval.

Reject:
- POST /api/matches/{matchId}/chat-decision { "decision": "REJECTED" }
- Tratarlo como cancelación unilateral, no como request consensuada.
- Después de success, salir del chat/Home refresh.

Visual approval:
- POST /api/matches/{matchId}/visual-decision { "decision": "REJECTED" } debe cerrar la instancia visual para el usuario que rechazo, independientemente de la decision del partner.
- Despues de success, el match no deberia volver en activeMatches[] como VISUAL_PHASE para ese usuario.
- POST { "decision": "APPROVED" } debe persistir la decision visual del usuario y devolver un estado consistente para que el front no permita decidir de nuevo.

Pendiente/no implementado:
- Request consensuado de aprobación tipo chat-approval-request.
- Safety report por matchId.
- Reasons SPAM/OFF_PLATFORM.
- Regla dev/prod de matchmaking: no rematchear al mismo par de usuarios dentro de la misma sesion de busqueda/matchmaking, con el criterio de "misma sesion" a definir en backend.
