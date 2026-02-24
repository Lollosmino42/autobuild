import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

public final class Autobuild {
	private static final Path CWD_PATH = Path.of(System.getProperty("user.dir"))
		.toAbsolutePath();
	private static final String CWD = CWD_PATH.toString();
	private static final String AUTOBUILD_CACHE = ".autobuild-cache";
	private static final String AUTOBUILD_BIN = ".autobuild-bin";


	private static void errlog( String message) {
		System.err.printf("[AUTOBUILD ERROR] : %s\n", message);
	}
	private static void errlog( String format, Object... args) {
		errlog( format.formatted( args));
	}

	private static void log( String message) {
		System.out.printf("[AUTOBUILD] : %s\n", message);
	}
	private static void log( String format, Object... args) {
		log( format.formatted( args));
	}


	public static void main(String[] args) { 
		if (args.length == 0) {
			errlog("Autobuild requires one or more arguments: <main class> <build files...> <javac flags...>");
			System.exit(1);
		}

		setup();

		// Extract parameters from args
		String mainClass = args[0];

		Set<Path> buildFiles = new HashSet<Path>();
		var flags = new HashSet<String>();

		for (int i = 1; i < args.length; ++i) {
			if (args[i].startsWith("-")) {
				flags.add( args[i]);
			}
			else buildFiles.add( Path.of(args[i]).toAbsolutePath());
		}

		// Check for help flag
		if ( flags.contains("-help")) {
			help();
			return;
		}
		// Check for clean flag
		if ( flags.contains("-clean")) {
			log( "Cleaning binary and cache directories");
			cleanCache();
			cleanBin();
			flags.remove("-clean");
		}

		// Find all files if no selection was specified
		if ( buildFiles.isEmpty()) {
			buildFiles = findJavaFiles(CWD_PATH).orElseThrow();
		}

		build(buildFiles, flags);
		execute(mainClass);

	}


	private static String promptDeletion(Path deletion) throws IOException {
		System.out.printf(" :: '%s' already exists and is not a directory. Do you want to delete it? [y/N] ",
				deletion);
		var promptReader = new BufferedReader(new InputStreamReader(System.in));
		String answer = promptReader.readLine();
		promptReader.close();
		return answer;
	}


	private static void setup() {
		try {

		{
			Path cache = Path.of(CWD, AUTOBUILD_CACHE);

			if ( Files.notExists(cache)) {
				Files.createDirectory(cache);
				return;
			}

			if ( !Files.isDirectory(cache)) {
				String answer = promptDeletion(cache);
				if (!answer.toLowerCase().equals("y")) {
					throw new InterruptedException();
				}
				Files.deleteIfExists(cache);
			}
		}
		{
			Path bin = Path.of(CWD, AUTOBUILD_BIN);

			if (Files.notExists(bin)) {
				Files.createDirectory(bin);
			}
			else if ( !Files.isDirectory(bin)) {
				String answer = promptDeletion(bin);
				if (!answer.toLowerCase().equals("y")) {
					throw new InterruptedException();
				}
				Files.deleteIfExists(bin);
			}
		}

		}
		catch (Exception e) {
			errlog("Could not setup cache");
			e.printStackTrace();
			System.exit(3);
		}
	}


	private static Set<Path> find(Path dir) {
		var files = new HashSet<Path>();
		try {
			var stream = Files.newDirectoryStream(dir);
			for (Path fp : stream) {

				String fileName = fp.toFile().getName();
				if (fileName.startsWith(".") || fileName.equals("Autobuild.java")) {
					continue;
				}
				if (fileName.endsWith(".java")) {
					files.add(fp.toAbsolutePath());
				}
				if (Files.isDirectory(fp)) {
					files.addAll( find(fp));
					continue;
				}
			}
			stream.close();
		}
		catch (Exception e) {
			e.printStackTrace();
			System.exit(2);
		}
		return files;
	}


	private static Optional<Set<Path>> findJavaFiles(Path dir) {
		if (!Files.isDirectory(dir)) {
			errlog("'%s' is not a directory\n", dir);
			return null;
		}
		return Optional.ofNullable( find(dir));
	}

	
	private static Path[] inAutobuild( Path file) {
		int namelength = file.getNameCount();
		int count;
		String cwd = CWD_PATH.getName(CWD_PATH.getNameCount() - 1).toString();
		for (count = 1; count < namelength; ++count) {
			if (file.getName(namelength - count)
					.toString()
					.equals(cwd))
				break;
		}

		String subp = file.subpath(namelength - count + 1, namelength).toString();
		Path binary = Path.of( CWD, AUTOBUILD_BIN, subp.replace(".java",".class"));
		Path cached = Path.of( CWD, AUTOBUILD_CACHE, subp);

		return new Path[] { binary, cached };
	}


	// Check whether A has different data from B
	private static int check( Path file) {
		try {
			var autobuild = inAutobuild(file);
			Path binary = autobuild[0];
			Path cached = autobuild[1];

			if (Files.notExists(binary)) {
				return 1;
			}

			if (Files.notExists(cached)) {
				Files.createDirectories(cached.getParent());
				Files.createFile(cached);
				Files.write( cached, Files.readAllLines(file));
				return 1;
			}

			var result = Files.readString(file)
				.compareTo(Files.readString(cached));

			if (result != 0) {
				Files.deleteIfExists(cached);
			}
			return result;
		}
		catch (Exception e) { 
			errlog("Something went wrong in check");
			e.printStackTrace();
			System.exit(4);
		}
		return 69;
	}


	private static void clean(Path dir) {
		try {

		var stream = Files.newDirectoryStream(dir);
		for (var item : stream) {
			if (Files.isDirectory(item)) {
				clean(item);
			}
			Files.delete(item);
		}
		stream.close();

		}
		catch (Exception e) {
			errlog("Could not clean '%s'", dir.toString());
			e.printStackTrace();
		}
	}
	private static void cleanCache() { clean( CWD_PATH.resolve( AUTOBUILD_CACHE)); }
	private static void cleanBin() { clean( CWD_PATH.resolve( AUTOBUILD_BIN)); }


	public static void build(Set<Path> files, Set<String> flags) {
		
		log("Building at ".concat(CWD));

		String[] command;
		{
			// Command template
			var commandList = new ArrayList<String>(3 + files.size() + flags.size());
			commandList.addAll( List.of("javac","-d",Path.of(AUTOBUILD_BIN).toAbsolutePath().toString()));
			// Add user arguments to commandList
			if (!flags.isEmpty()) commandList.addAll(flags);

			// Filter files
			var filtered = new HashSet<String>(files.size());
			for (Path p : files) {
				if (check(p) != 0) filtered.add(p.toString());
			}

			// If there are no files to recompile the function can return
			if (filtered.isEmpty())
				return;

			commandList.addAll( filtered);
			command = new String[commandList.size()];
			command = commandList.toArray(command);
		}

		log("Compilation commandList -> %s", String.join(" ", command));
		
		try {
			// Compile
			var compile = Runtime.getRuntime().exec( command);
			int status = compile.waitFor();
			if (status != 0) {
				var errorReader = compile.errorReader();
				errorReader.lines().forEach(System.out::println);
				errorReader.close();
				System.exit(status);
			}
		}
		catch (Exception e) {
			errlog("Could not build");
			//e.printStackTrace();
		}
	}

	private static void execute(String mainClass) {

		try {
			// Execute build
			/*
			var execution = Runtime.getRuntime()
				.exec(new String[] {"java","-cp",AUTOBUILD_BIN,mainClass});
				*/
			var executor = new ProcessBuilder(new String[] {"java","-cp",AUTOBUILD_BIN,mainClass});
			executor.inheritIO();

			log( "Running %s", mainClass);
			System.out.println();

			var execution = executor.start();

			int status = execution.waitFor();
			if (status != 0) {
				var errorReader = execution.errorReader();
				errorReader.lines().forEach(System.out::println);
				errorReader.close();
				System.exit(status);
			}
		}
		catch (Exception e) {
			errlog("Could not run");
			e.printStackTrace();
		}
	}


	private static void help() {
		System.out.println("""
				This is a help message!
				-help  -> display this message and quit. Note that this flag renders any other parameter useless
				-clean -> clear binary and cache folders before building
				""");
	}
}
