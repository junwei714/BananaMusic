lines = []
with open('app/src/main/java/my/edu/utar/bananamusic/utils/AudioPlayerHelper.java', 'r') as f:
    lines = f.readlines()

new_lines = []
skip_mode = False
has_seen_updatePlaybackState = False
has_seen_updateInternalPlaybackState = False

for line in lines:
    if 'private void updateInternalPlaybackState()' in line:
        if not has_seen_updateInternalPlaybackState:  # Keep only the first occurrence
            new_lines.append(line)
            has_seen_updateInternalPlaybackState = True
        else:
            skip_mode = True  # Skip this duplicate method
    elif 'private void updatePlaybackState()' in line:
        if not has_seen_updatePlaybackState:  # Keep only the first occurrence
            new_lines.append(line)
            has_seen_updatePlaybackState = True
        else:
            skip_mode = True  # Skip this duplicate method
    elif skip_mode and line.strip() == '}':
        skip_mode = False  # Stop skipping after end of method
        continue
    elif not skip_mode:
        new_lines.append(line)

with open('app/src/main/java/my/edu/utar/bananamusic/utils/AudioPlayerHelper.java.fixed', 'w') as f:
    f.writelines(new_lines)

print("Done! Check AudioPlayerHelper.java.fixed") 