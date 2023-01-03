package de.mpicbg.ulman.fusion;

import picocli.CommandLine;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

@CommandLine.Command(name = "ParamsTest", version = "0.123", description = "some global info", mixinStandardHelpOptions = true)
@Plugin(type = Command.class, menuPath = "Plugins>ParamsTest")
public class ParamsTest implements Command {

	@CommandLine.Option(names = {"-p1","param1"}, description = "parameter 1")
	@Parameter(label = "param1")
	int param = 0;

	@Override
	public void run() {
		System.out.println("Hello from ParamsTest, param = "+param);
	}

	// ================================ CLI ==========================
	public static void main(String[] args) {
		new CommandLine(new ParamsTest()).execute(args);
	}
}