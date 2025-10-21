import json
import matplotlib.pyplot as plt
import os

# List your JSONL files here
files = [
    "logs/storage_small-delta.json",
    "logs/storage_big-delta.json"
]

fig, axes = plt.subplots(1, len(files), figsize=(10 * len(files), 5), sharey=True)

if len(files) == 1:
    axes = [axes]  # handle single plot case

for ax, filename in zip(axes, files):
    avg_full, avg_sent, avg_merge = [], [], []

    with open(filename, "r") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            data = json.loads(line)
            metrics = data["GLOBAL"]["samplesPerProtocol"]["9898"]["metricSamples"]
            for m in metrics:
                if m["metricName"] == "averageFullStateSize":
                    avg_full.append(m["samples"][0]["value"] / 1024)
                elif m["metricName"] == "averageStateSizeSent":
                    avg_sent.append(m["samples"][0]["value"] / 1024)
                elif m["metricName"] == "averageTimeMerging":
                    avg_merge.append(m["samples"][0]["value"])

    x = range(len(avg_full))
    title = "" # os.path.basename(filename).replace(".json", "")
    if "small-delta" in filename:
        title = "δ-CRDT"
    elif "big-delta" in filename:
        title = "Δ-CRDT"
    else:
        title = "State-based CRDT"

    ax.plot(x, avg_full, marker='o', label='Full State Size (kB)')
    ax.plot(x, avg_sent, marker='x', label='State Sent (kB)')
    ax.set_xlabel("Minutes")
    ax.set_ylabel("kB")
    ax.set_title(title)
    ax.grid(True)
    ax.legend()

plt.suptitle("CRDT State Sizes Comparison", fontsize=14)
plt.tight_layout()
plt.show()
