Halma - Instrucoes de build e execucao

Resumo
- Este projeto implementa um servidor e um cliente simples para o jogo Halma.
- O arquivo principal empacotado e `halma.jar`. O launcher aceita flags:
  - `-server` inicia o servidor
  - `-serverstop` tenta parar instancias do servidor
  - sem flags, abre a tela do cliente (GUI) para conectar

Requisitos
- Java 21 LTS (JRE/JDK instalado)
- Windows (instrucoes fornecidas para PowerShell). Em Linux/OSX alguns comandos variam.
- Maven 3.8.1+ (para compilacao com pom.xml)

Como compilar (opcional)
1. Abra PowerShell na raiz do projeto (onde estao os .java)
2. Compile com na raiz do projeto:
```
   $files=(Get-ChildItem -Recurse -Filter *.java | Where-Object { $_.FullName -notmatch 'root_backup_OLD' } | ForEach-Object FullName); javac -d . $files
```
3. Se preferir usar o script de build que ja gera o `halma.jar` execute:
   ./build.ps1

Executando o servidor
- Recomendo iniciar o servidor em apenas UMA maquina (ou VM). Os outros dispositivos devem iniciar o cliente e conectar a esse servidor.
- Iniciar:
```
  java -jar halma.jar -server
```

Parar o servidor
- Use a flag integrada para parar possiveis instancias:
```
  java -jar halma.jar -serverstop
```

Firewall (Windows)
- Para permitir conexoes na porta 9090 (Admin PowerShell):
  New-NetFirewallRule -DisplayName "Halma Server (9090)" -Direction Inbound -Action Allow -Protocol TCP -LocalPort 9090 -Profile Any
- O projeto tenta empacotar `open-firewall.ps1` dentro do JAR e o servidor tenta executar o script ao iniciar. Isso precisa de privilegios admin para realmente criar a regra.

Conectar como cliente (outra maquina/VM)
1. Descubra o IP da maquina que executa o servidor (na VM execute `ipconfig` e anote o IPv4, ex.: 192.168.1.42).
2. No cliente execute:
```
   java -jar halma.jar <IP marquina servidor>
```


Notas sobre VMs e rede
- Bridged: a VM recebe IP na mesma rede do host. Recomendado para testes locais, pois o host e outras maquinas veem a VM diretamente.

