package dslab.util.parser;

import dslab.util.DMTPState;

import java.util.LinkedList;
import java.util.List;

public class DMTPParser {

    private final IDMTPParserListener listener;
    private DMTPState state = DMTPState.WAITING;

    public DMTPParser(IDMTPParserListener listener) {
        this.listener = listener;
    }

    public void parse(String input) throws ParserException {
        if (input == null)
            return;

        input = input.trim();

        if (input.equals("quit")) {
            listener.onQuitCommand();
            return;
        }

        switch (state) {
            case WAITING:
                if (input.equals("begin")) {
                    state = DMTPState.RECEIVING_MESSAGE;
                    listener.onBeginCommand();
                } else {
                    throw new ParserException();
                }
                break;

            case RECEIVING_MESSAGE:
                if (input.startsWith("to")) {
                    var recipients = parseToCommand(input);
                    listener.onToCommand(recipients);

                } else if (input.startsWith("subject")) {
                    if (input.length() < 8)
                        listener.onSubjectCommand("");
                    // remove "subject " from the line (7 letters plus one space)
                    var subject = input.substring(8);
                    listener.onSubjectCommand(subject);

                } else if (input.startsWith("data")) {
                    if (input.length() < 5)
                        listener.onDataCommand("");
                    // remove "data " from the line (4 letters plus one space)
                    var data = input.substring(5);
                    listener.onDataCommand(data);

                } else if (input.startsWith("from")) {
                    if (input.length() < 5)
                        listener.onFromCommand("");
                    // remove "from " from the line (4 letters plus one space)
                    var from = input.substring(5);
                    listener.onFromCommand(from);

                } else if (input.startsWith("hash")) {
                    if (input.length() < 5)
                        listener.onFromCommand("");
                    // remove "hash " from the line (4 letters plus one space)
                    var hash = input.substring(5);
                    listener.onHashCommand(hash);
                } else if (input.equals("send")) {
                    if (listener.onSendCommand())
                        state = DMTPState.WAITING;

                } else {
                    throw new ParserException();
                }
                break;
        }
    }

    private List<String> parseToCommand(String input) {
        if (input.length() < 3)
            return List.of();

        List<String> recipientList = new LinkedList<>();

        String addressList = input.substring(3);

        String[] addresses = addressList.split(",");
        for (int i = 0; i < addresses.length; i++) {
            addresses[i] = addresses[i].trim();
            recipientList.add(addresses[i]);
        }

        return recipientList;
    }
}
