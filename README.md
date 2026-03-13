# 🎨 CrossPro - MIUI & HyperOS Theme Editor & Bypass

O **CrossPro** (também conhecido como CrossS MTZ) é uma solução avançada e de código aberto para entusiastas de customização da MIUI/HyperOS. Ele permite que você desmonte, edite e aplique temas `.mtz` de terceiros, superando as restrições impostas pelo Gerenciador de Temas oficial da Xiaomi.

---

## 🌟 Funcionalidades Principais

### 📂 Manipulação de Arquivos MTZ
- **Extração Inteligente**: Descompacta módulos específicos do tema (SystemUI, Framework, Ícones, etc.) para edição individual.
- **Re-empacotamento Automático**: Re-zipa os módulos alterados e gera um novo arquivo `.mtz` pronto para uso.
- **Navegação de Estrutura**: Explore a árvore de arquivos interna do tema como um explorador de arquivos comum.

### 📝 Editor de Metadados e Info
- Altere o **Título**, **Autor**, **Designer** e **Versão** do tema.
- Edite o arquivo `description.xml` através de uma interface amigável.
- Suporte a múltiplas linguagens e versões de adaptador MIUI.

### 🎨 Edição de Recursos (Colors & XML)
- **Editor de Cores**: Interface visual para modificar valores hexadecimais em arquivos de cores (`colors.xml`).
- **Substituição de Assets**: Troque papéis de parede (`wallpaper`), ícones e pré-visualizações (`preview`) com apenas alguns cliques.

### 🛡️ Sistema de Bypass (Anti-Reversão)
- **Serviço de Acessibilidade**: Monitora ativamente o Gerenciador de Temas da MIUI para interceptar tentativas de restauração do tema padrão.
- **Background Monitor Thread**: Uma thread dedicada que roda em um `ForegroundService` para garantir que o processo não seja morto pelo sistema e restaure o tema automaticamente caso a MIUI o remova.

### ⚡ Integração com Shizuku
- Aplica temas diretamente na pasta protegida `/data/user/0/com.android.thememanager/files/snapshot/` sem necessidade de Root.

---

## 🛠️ Arquitetura Técnica

O CrossPro opera em três camadas principais:
1. **Camada de Edição (App)**: Gerencia o ciclo de vida dos arquivos temporários e a UI de edição.
2. **Camada de Privilégios (Shizuku)**: Utilizada para realizar operações de escrita em diretórios do sistema que o aplicativo normalmente não teria acesso.
3. **Camada de Persistência (AccessibilityService)**: Atua como uma sentinela. Se o sistema MIUI detectar um tema "não autorizado" e tentar removê-lo, o serviço detecta a ausência do arquivo de snapshot e o restaura instantaneamente a partir do backup salvo no `SharedPreferences`.

---

## 🚀 Como Começar

### Pré-requisitos
- Dispositivo com **MIUI** ou **HyperOS**.
- **Android 10** ou superior.
- **Shizuku** instalado e configurado ([Link para download](https://shizuku.rikka.app/)).

### Passo a Passo
1. **Ative o Shizuku**: Inicie o Shizuku e autorize o CrossPro.
2. **Importe um Tema**: Clique em "Selecionar MTZ" e escolha seu arquivo de tema.
3. **Edite**: Navegue pelos componentes, altere cores ou metadados conforme desejar.
4. **Configure a Acessibilidade**:
    - Vá nas configurações do sistema e ative o **Serviço de Acessibilidade do CrossPro**.
    - Isso é crucial para que o bypass de "tempo expirado" funcione.
5. **Aplique**: Clique no botão "Aplicar Tema". O app irá injetar o tema e abrir o instalador oficial da MIUI para confirmar.

---

## ⚠️ Solução de Problemas

- **O tema volta para o padrão após alguns minutos**:
    - Verifique se o Serviço de Acessibilidade está ativado.
    - Desative as "Otimizações de Bateria" para o CrossPro (defina como "Sem restrições").
- **Shizuku não conecta**:
    - Certifique-se de que a Depuração Sem Fio (Wireless Debugging) está ativa nas Opções de Desenvolvedor.
- **Erro ao abrir o MTZ**:
    - O arquivo pode estar corrompido ou protegido por criptografia de terceiros incompatível.

---

## 📄 Licença e Aviso Legal

Este projeto é destinado apenas para fins de personalização pessoal. Não incentivamos a pirataria de temas pagos da loja oficial da Xiaomi. Use com responsabilidade.

---
*Desenvolvido com ❤️ para a comunidade de customização Android.*
