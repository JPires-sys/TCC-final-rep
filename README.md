# Projeto TCC
Supervisõa parental nas Redes Sociais

# Objetivo
Construir um Aplicativo Android que supervisione os jovens nas redes sociais

# Banco de Dados
Firebase -> NoSQL

# Base
Em Mudança


## Chatbox e vínculo por ID

O jovem pode abrir **Conversar com o Chatbox** na tela inicial. Mensagens e rascunho são guardados no aparelho enquanto a conversa está aberta. Ao sair pelo botão Voltar ou pelo retorno do Android, a sessão completa é enviada para `historicoChat/{uid}/{data}/{idConversa}` no Firebase. Se o envio falhar, uma cópia local permite tentar novamente. **Histórico por data** consulta as conversas anteriores sem substituir a sessão atual.

A mensagem de abertura do Gemini pode usar, em um dia a cada três, os aplicativos com maior tempo registrado nos últimos sete dias em `historicoUso/{uid}`. Se os dados não estiverem disponíveis, faz uma pergunta geral. Sair e voltar recupera a mesma conversa e o rascunho, sem repetir a abertura. Cada saída atualiza o mesmo registro no banco, mantendo a data original da conversa. Sem sessão local, o app tenta recuperar a última salva.

As perguntas do responsável e o botão **Gerar relatório com Gemini**, na tela de histórico do jovem, incluem tempo de tela e conversas salvas dos jovens vinculados. O contexto utiliza até 100 mensagens entre as últimas 30 datas, com limite de tamanho e indicação de dados ausentes. O relatório individual inclui apenas o jovem selecionado.

O cadastro de pai e jovem valida os dígitos verificadores do CPF e rejeita sequências repetidas. Telefones precisam de DDD e 10 ou 11 dígitos nacionais; formatação e código +55 são aceitos. CPF e telefone são gravados somente com números. Essa validação é de formato e dígitos, sem consulta cadastral à Receita ou confirmação da linha telefônica.

Ambos os perfis têm **Vincular contas pelo ID**, com o próprio UID para copiar e um campo para informar o UID da outra pessoa. O ID no cadastro do jovem é opcional. A lista de filhos consulta `idResponsavel` pelo UID autenticado e acompanha atualizações em tempo real, sem depender de um e-mail recebido por Intent.

Configuração, estrutura do banco, fragmento de regras e roteiro de testes: [firebase/README.md](firebase/README.md).

Validação local:

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```
