#include <unistd.h>
#include <stdlib.h>
#include <string.h>

/*
 * Small executable in C to run jar file "directly",
 * because i don't know how to bash
 *
 * Featuring a string builder
 */

typedef struct {
	size_t length, capacity;
	char **items;
} CMD;

CMD cmd_new(size_t capacity) {
	return (CMD) {
		.capacity = capacity,
		.length = 0,
		.items = calloc(capacity, sizeof(void*))
	};
}

void cmd_append( CMD *argv, char* arg) {
	if (argv->length >= argv->capacity ) {
		argv->capacity *= 2;
		argv->items = realloc(argv->items, argv->capacity);
	}
	argv->items[argv->length++] = arg;
}

void cmd_free(CMD *cmd) {
	free(cmd->items);
}


int main(int argc, char **argv) {
	CMD cmd = cmd_new(10);
	cmd_append(&cmd, "java -jar Autobuild.jar");

	for (int i = 1; i < argc; ++i) {
		cmd_append(&cmd, argv[i]);
	}

	//printf("Dir: %s\n", cwd);

	size_t command_len = strlen(cmd.items[0]);
	for (int i = 0; i < cmd.length; ++i)
		command_len += strlen(cmd.items[i]) + 1;
	
	// Build command
	char *command = malloc(command_len + cmd.length);

	for (int i = 0; i < cmd.length; ++i) {
		strcat(command, cmd.items[i]);
		strcat(command, " ");
	}
	//printf("Command :: %s\n", command);
	int status = system(command);

	free(command);
	cmd_free(&cmd);

	return status;
}
