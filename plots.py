import json
import matplotlib.pyplot as plt

filename = "results/storage_state.json"

average_full_state_sizes = []
average_state_sizes_sent = []
average_merge_times = []

with open(filename, "r") as f:
    for line in f:
        line = line.strip()
        if not line:
            continue
        data = json.loads(line)
        protocol_data = data["GLOBAL"]["samplesPerProtocol"]["9898"]["metricSamples"]
        for metric in protocol_data:
            if metric["metricName"] == "averageFullStateSize":
                average_full_state_sizes.append(metric["samples"][0]["value"] / 1024)
            elif metric["metricName"] == "averageStateSizeSent":
                average_state_sizes_sent.append(metric["samples"][0]["value"] / 1024)
            elif metric["metricName"] == "averageTimeMerging":
                average_merge_times.append(metric["samples"][0]["value"])

x = list(range(len(average_full_state_sizes)))

plt.figure(figsize=(10,5))
plt.plot(x, average_full_state_sizes, marker='o', label='Average Full State Size (kB)')
plt.plot(x, average_state_sizes_sent, marker='x', label='Average State Size Sent (kB)')
plt.xlabel("Minutes")
plt.ylabel("kB")
plt.title("CRDT State Sizes Over Time (Full and Disseminated)")
plt.legend()
plt.grid(True)
plt.tight_layout()
plt.show()

# Plot merge time
# plt.figure(figsize=(10,5))
# plt.plot(x, average_merge_times, marker='o', color='r', label='Average Merge Time (ms)')
# plt.xlabel("Minutes")
# plt.ylabel("Milliseconds")
# plt.title("CRDT Merge Time Over Time")
# plt.legend()
# plt.grid(True)
# plt.tight_layout()
# plt.show()
