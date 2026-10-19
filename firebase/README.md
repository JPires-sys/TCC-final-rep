# Vínculos e Chatbox no Realtime Database

O app usa o projeto de `app/google-services.json` e a conta do Firebase Authentication.

## Conversas: armazenamento local e envio ao sair

- Enquanto a tela está aberta, mensagens e rascunho são gravados em um arquivo privado por UID em `noBackupFilesDir/chatbox`. A gravação atômica mantém a última cópia completa se houver interrupção.
- Ao voltar à tela após rotação ou encerramento do processo, a mesma sessão local é recuperada, inclusive após sair normalmente pelo botão Voltar. A cópia ativa continua no aparelho após o envio ao banco; na próxima saída, o mesmo ID é atualizado. Sem cópia ativa, o app tenta retomar a última conversa pendente ou salva no banco. Uma pergunta que ficou sem resposta pode ser retomada pela IA.
- Ao tocar em **Voltar** ou usar o retorno do Android, a chamada pendente da IA é cancelada e a sessão completa é marcada localmente para envio. Somente então o Firebase recebe a conversa.
- Caminho: `historicoChat/{uid}/{yyyy-MM-dd}/{idConversa}`. A data é a data local de início da sessão; cruzar a meia-noite não muda esse agrupamento. Cada sessão tem um UUID, portanto duas conversas no mesmo dia não se sobrescrevem.
- Campos: `id`, `date`, `startedAt`, `endedAt` e `mensagens/{id}` com `id`, `role`, `content` e `createdAt`. Horários são os do aparelho em milissegundos. Os IDs sequenciais preservam a ordem das mensagens. Texto digitado que ainda não foi enviado não é transmitido como mensagem.
- A cópia pendente só é removida do aparelho após confirmação do banco. A tentativa usa o mesmo caminho e os mesmos dados, evitando duplicação quando o servidor salvou mas a confirmação se perdeu.
- Se não houver conexão ou permissão, a tela oferece **Tentar novamente** ou **Sair com cópia local**. Pendências são reenviadas na próxima saída. Fechar pelo botão Home ou encerrar o processo não dispara envio: mantém a sessão local.
- **Histórico por data** lê até 30 datas por página e permite carregar datas anteriores. Conversas pendentes também aparecem, identificadas como locais. Abrir um registro não o mistura à conversa atual nem o envia ao Gemini.
- As conversas só ficam visíveis em outro aparelho depois do envio confirmado. Desinstalar o app ou limpar seus dados remove cópias que ainda não foram enviadas.

## Mensagem diária e tempo de tela

O Chatbox usa `GeminiConfig.getBackendUrl()`, o mesmo backend das análises do app. A mensagem diária é guardada por conta e data neste aparelho. Retomar uma conversa existente mantém as mensagens, sem gerar outra abertura. O cache diário é utilizado quando uma abertura nova é necessária.

Em um dia a cada três (escolha estável por data e UID), o app consulta `historicoUso/{uid}/{yyyy-MM-dd}/{pacoteComUnderscores}`, dos últimos sete dias. Os valores já são minutos, conforme `MonitoramentoService`. O resumo agrega os cinco aplicativos com maior uso registrado e é enviado ao Gemini como contexto apenas para a abertura. Não transforma automaticamente uso elevado em diagnóstico ou problema. Sem dados, conexão ou permissão, a consulta expira em oito segundos e a pergunta é geral. Nos demais dias não consulta o tempo de tela.

Durante o diálogo, o Gemini recebe até 40 mensagens da sessão atual. Não envia conversas anteriores consultadas pelo histórico. Erros HTTP ou respostas inválidas não são armazenados como falas da IA.

## Regras e caminho corrigido

As regras fornecidas pelo usuário em 16/10/2026 já permitem leitura e escrita a usuários autenticados em `historicoChat` e `historicoUso`. O erro anterior ocorria porque o código usava `chatbox`, caminho sem permissão nessas regras. O novo código usa `historicoChat`; não exige publicar regras para adicionar outro caminho.

`database.rules.fragment.json` é um fragmento opcional de regras por UID e validação do formato, com as regras de vínculo existentes. Não é substituto completo das regras em produção e não foi publicado. Permissões amplas em um nó ancestral continuam valendo e não são restringidas por regras filhas. Preserve regras de cadastro, consultas, alertas, logs e demais recursos ao integrar qualquer fragmento. Esta alteração não modifica as permissões do banco ativo nem migra registros de outros formatos/caminhos.

## Vínculos

- `usuarios/pais/{uid}` e `usuarios/filhos/{uid}` usam o UID do Authentication.
- `usuarios/filhos/{uid}/idResponsavel` contém o UID do responsável.
- O vínculo pode ser iniciado por qualquer uma das duas contas e não substitui um responsável já associado.
- Contas antigas baseadas em e-mail precisam de migração própria; o app não associa contas por suposição.

## Verificação manual

1. Entrar na conta de um jovem, abrir Chatbox e enviar uma mensagem fictícia. A conversa deve funcionar sem gravar no banco enquanto a tela estiver aberta.
2. Girar a tela e reiniciar o app: verificar mensagens e rascunho recuperados do aparelho.
3. Sair usando **Voltar**. Conferir a sessão em `historicoChat/{uid}/{data}/{id}` e, ao reabrir, acessar **Histórico por data**.
4. Sair, reabrir e continuar: deve recuperar mensagens e rascunho, sem nova abertura. Ao sair de novo, o mesmo ID no banco deve conter as mensagens novas sem duplicação.
5. Desligar a rede antes de sair, aguardar o aviso e escolher a cópia local. Reabrir, verificar a indicação de pendência, religar a rede e sair novamente. Conferir que não há duplicação.
6. Sair enquanto uma resposta está sendo gerada: a mensagem do jovem deve ser preservada, sem resposta tardia após o encerramento.
7. Conferir uma abertura contextual com dados de uso fictícios. Dados ausentes ou negados devem permitir a abertura geral.

## Validação

Compilação debug e 35 testes passaram. Os testes automatizados simulam o Firebase e o disco e verificam serialização JSON, persistência local, recuperação, envio ao sair, idempotência, troca de conta, seleção do contexto e falhas de rede. A confirmação do banco ativo exige o roteiro manual com uma conta de teste.

Comandos: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug`.

Neste Windows, use o Java do Android Studio. Se houver falha de sockets Unix no Gradle, a configuração local de teste já usada foi `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:\Users\Workstation\AndroidStudioProjects\Supervisionador\gradlew.bat`, para forçar fallback para TCP sem alterar configurações globais.

## Análise solicitada pelo responsável

A pergunta na tela inicial do pai e o botão **Gerar relatório com Gemini** do relatório individual usam `ParentAnalysisRepository`. O responsável autenticado e o vínculo `usuarios/filhos/{uid}/idResponsavel` são verificados antes de ler os dados e novamente antes de transmiti-los ao Gemini. O relatório individual não inclui outros jovens. Não são enviados CPF, telefone, e-mail ou senha no contexto.

O contexto reúne `historicoUso/{uid}` (valores em minutos) e `historicoChat/{uid}`, consultando até 30 datas e até 100 mensagens recentes por jovem. Falas do jovem e da IA têm rótulos diferentes. Textos longos são limitados a 2.000 caracteres por mensagem e o contexto total tem orçamento de tamanho, com indicação de abreviação. Falhas de leitura são explicitadas no prompt; não são tratadas como ausência de conversas. Mensagens ainda somente no aparelho não entram no relatório.

As regras compartilhadas pelo usuário já permitem a leitura autenticada nesses caminhos. O fragmento opcional foi atualizado para contemplar a leitura do histórico pelo responsável vinculado, além do próprio jovem; não foi publicado no banco ativo.

## Validação de cadastro

`RegistrationValidation` verifica os dois dígitos verificadores do CPF, rejeita todos os dígitos repetidos e aceita a máscara padrão. O telefone aceita 10 ou 11 números com DDD, com ou sem máscara e +55 opcional. Os mesmos critérios são aplicados nas telas de cadastro e no DAO antes de qualquer criação no Authentication. São validações locais; não consultam a situação cadastral do CPF nem a existência da linha.
