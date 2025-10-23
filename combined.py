import json
import matplotlib.pyplot as plt
import os

files = [
    "transitive/storage_state.json",
    "transitive/storage_small-delta.json",
    "transitive/storage_big-delta.json"
]

SAVE = True

# Combined figure
fig, axes = plt.subplots(1, len(files), figsize=(12 * len(files), 8), sharey=True)
if len(files) == 1:
    axes = [axes]

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
    title = ""
    if "small-delta" in filename:
        title = "δ-CRDT (transitive)"
    elif "big-delta" in filename:
        title = "Δ-CRDT"
    else:
        title = "State-based CRDT"

    # Plot in combined figure
    ax.plot(x, avg_full, marker='o', label='Full State Size (kB)')
    ax.plot(x, avg_sent, marker='x', label='State Sent (kB)')
    ax.set_xlabel("Minutes")
    ax.set_ylabel("kB")
    ax.set_title(title)
    ax.grid(True)
    ax.legend()

    # Save individual subplot as separate figure
    if SAVE:
        fig_single, ax_single = plt.subplots(figsize=(10,5))
        ax_single.plot(x, avg_full, marker='o', label='Full State Size (kB)')
        ax_single.plot(x, avg_sent, marker='x', label='State Sent (kB)')
        ax_single.set_xlabel("Minutes")
        ax_single.set_ylabel("kB")
        ax_single.set_title(title)
        ax_single.grid(True)
        ax_single.legend()
        fig_single.tight_layout()
        filename_out = filename.replace(".json", "_plot.png").split("/")[-1]
        fig_single.savefig(filename_out)
        plt.close(fig_single)  # close to free memory

plt.suptitle("CRDT State Sizes Comparison (System grows 50%)", fontsize=14)
plt.tight_layout()
if SAVE:
    plt.savefig("plot_normal_transitive.png")
plt.show()
