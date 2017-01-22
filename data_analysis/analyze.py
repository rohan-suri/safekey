#!/bin/python
import json
from sklearn.cluster import KMeans
import numpy as np
import matplotlib.pyplot as plt

def convert_to_ts(keystrokes, bin_size):
	start_time = keystrokes[0]['time']
	end_time = keystrokes[len(keystrokes) - 1]['time']

	bins = (end_time-start_time) / bin_size
	ts = [0] * (bins+1)
	for key in keystrokes:
		# print "ts length = " + str(len(ts))
		# print "index = " + str((key['time']-start_time)/bin_size)
		ts[(key['time']-start_time)/bin_size] = ts[(key['time']-start_time)/bin_size] + 1
	return ts

def convert_to_string(keystrokes, bin_size):
	start_time = keystrokes[0]['time']
	end_time = keystrokes[len(keystrokes) - 1]['time']

	bins = (end_time-start_time) / bin_size
	ts = [""] * (bins+1)
	for key in keystrokes:
		# print "ts length = " + str(len(ts))
		# print "index = " + str((key['time']-start_time)/bin_size)
		if not key['is_function']:
			ts[(key['time']-start_time)/bin_size] = ts[(key['time']-start_time)/bin_size] + "" + chr(key['key_id'])

	string = ""
	for key in keystrokes:
		if not key['is_function']:
			# print key['key_id']
			string += chr(key['key_id'])
	print(len(ts))
	return ts

def print_char_list(char_list, bin_size):
	start = 0
	end = len(char_list)
	bins = len(char_list) / bin_size
	pretty_print = [""] * (bins + 1)
	for i in range(len(char_list)):
		pretty_print[i/bin_size] += char_list[i]

	for i in range(len(pretty_print)):
		print str(i*bin_size) + "\t" + str(pretty_print[i])


def plot(title, ts, fig_id):
	ts_for_kmeans = []
	for i in range(len(ts)):
		ts_for_kmeans.append([i, ts[i]])
	# X = np.array(ts_for_kmeans)
	# kmeans = KMeans(n_clusters=7, random_state=0).fit(X)
	# print kmeans.labels_
	# print kmeans.cluster_centers_
	# kmeans_cluster_vals = []
	# for item in kmeans.cluster_centers_:
	# 	kmeans_cluster_vals.append(item[0])
	# x = range(len(ts))
	# y = ts
	plt.figure(fig_id)
	plt.xticks(np.arange(0, 251, 20))
	plt.title(title)
	plt.plot(ts)
	plt.ylabel("Frequency")
	plt.grid(True)

with open('safe-key-export.json') as data_file:
    data = json.load(data_file)

rohan_data = data['1485039138206']
texting_driving = rohan_data['texting_driving']
texting_only = rohan_data['texting_only']

ts = convert_to_ts(texting_driving, 1000)
plot("1485032583934 texting_driving", ts,1 )
print "texting_driving:"
print_char_list(convert_to_string(texting_driving, 1000), 20)
print ""
print "texting_only"
print_char_list(convert_to_string(texting_only, 1000), 5)



ts = convert_to_ts(texting_only, 1000)
plot("1485032583934 texting_only", ts, 2 )
plt.show()

# for item in ts:
# 	print item
