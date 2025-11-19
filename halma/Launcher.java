package halma;

public class Launcher {
    public static void main(String[] args) {
        try {
            if (args.length > 0 && args[0].equalsIgnoreCase("-serverstop")) {
                stopServer();
                return;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("-server")) {
                String jarPath = System.getProperty("java.class.path");
                String os = System.getProperty("os.name").toLowerCase();
                boolean launched = false;
                try {
                        if (os.contains("win")) {
                        try {
                            ProcessBuilder pb = new ProcessBuilder("java", "-cp", jarPath, "halma.HalmaServer");
                            pb.redirectErrorStream(true);
                            pb.start();
                            launched = true;
                        } catch (Throwable e) {
                            ProcessBuilder pb2 = new ProcessBuilder("cmd", "/c", "start", "/B", "\"Halma Server\"", "java", "-cp", jarPath, "halma.HalmaServer");
                            pb2.redirectErrorStream(true);
                            pb2.start();
                            launched = true;
                        }
                    } else {
                        String cmd = String.format("nohup java -cp '%s' halma.HalmaServer > /dev/null 2>&1 &", jarPath);
                        ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd);
                        pb.start();
                        launched = true;
                    }
                } catch (Throwable t) {
                    System.err.println("Falha ao tentar iniciar o servidor em background: " + t.getMessage());
                }

                if (launched) {
                    System.out.println("Servidor iniciado em background.");
                    //return;
                }
                
                HalmaServer.main(new String[0]);
            } else {
                String host = (args.length > 0) ? args[0] : "localhost";
                HalmaClient.main(new String[] { host });
            }
        } catch (Exception e) {
            System.err.println("Erro ao iniciar: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void stopServer() {
        String os = System.getProperty("os.name").toLowerCase();
        System.out.println("Procurando instâncias de HalmaServer para parar...");
        try {
            if (os.contains("win")) {
                ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                    "Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -and ($_.CommandLine -match 'halma.HalmaServer') } | Select-Object -ExpandProperty ProcessId");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                java.util.List<String> pids = new java.util.ArrayList<>();
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim(); if (line.length() == 0) continue; 
                    if (line.matches("\\d+")) pids.add(line);
                }
                p.waitFor();
                if (pids.isEmpty()) { System.out.println("Nenhuma instância encontrada."); return; }
                for (String pid : pids) {
                    try {
                        ProcessBuilder kill = new ProcessBuilder("taskkill", "/PID", pid, "/F");
                        kill.redirectErrorStream(true);
                        Process kp = kill.start();
                        kp.waitFor();
                        System.out.println("Parado PID " + pid);
                    } catch (Exception ex) { System.err.println("Falha ao parar PID " + pid + ": " + ex.getMessage()); }
                }
            } else {
                ProcessBuilder pb = new ProcessBuilder("sh", "-c", "pgrep -f 'halma.HalmaServer' || true");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()));
                java.util.List<String> pids = new java.util.ArrayList<>();
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim(); if (line.length() == 0) continue; pids.add(line);
                }
                p.waitFor();
                if (pids.isEmpty()) { System.out.println("Nenhuma instância encontrada."); return; }
                for (String pid : pids) {
                    try {
                        ProcessBuilder kill = new ProcessBuilder("sh", "-c", "kill " + pid);
                        kill.redirectErrorStream(true);
                        Process kp = kill.start(); kp.waitFor();
                        System.out.println("Parado PID " + pid);
                    } catch (Exception ex) { System.err.println("Falha ao parar PID " + pid + ": " + ex.getMessage()); }
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao tentar parar servidores: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
