/**
 * Copyright 2023 LibreMobileOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.libremobileos.yifan.face;

import static com.libremobileos.yifan.face.FaceScanner.MAXIMUM_DISTANCE_TF_OD_API;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Implementation of Face Detection workload using FaceStorageBackend, based on FaceFinder */
public class FaceRecognizer {
	private final FaceStorageBackend storage;
	private final FaceFinder detector;

	private FaceRecognizer(Context ctx, FaceStorageBackend storage, int inputWidth, int inputHeight, int sensorOrientation, boolean hwAccleration, boolean enhancedHwAccleration, int numThreads) {
		this.storage = storage;
		this.detector = FaceFinder.create(ctx, inputWidth, inputHeight, sensorOrientation, hwAccleration, enhancedHwAccleration, numThreads);
	}

	public static FaceRecognizer create(Context ctx, FaceStorageBackend storage, int inputWidth, int inputHeight, int sensorOrientation, boolean hwAccleration, boolean enhancedHwAccleration, int numThreads) {
		return new FaceRecognizer(ctx, storage, inputWidth, inputHeight, sensorOrientation, hwAccleration, enhancedHwAccleration, numThreads);
	}

	public static FaceRecognizer create(Context ctx, FaceStorageBackend storage, int inputWidth, int inputHeight, int sensorOrientation) {
		return create(ctx, storage, inputWidth, inputHeight, sensorOrientation, false, true, 4);
	}

	/** Combination of FaceScanner.Face and FaceDetector.Face for face recognition workloads */
	public static class Face extends FaceScanner.Face {
		private final float confidence;

		public Face(String id, String title, Float distance, Float confidence, RectF location, Bitmap crop, float[] extra) {
			super(id, title, distance, location, crop, extra);
			this.confidence = confidence;
		}

		public Face(FaceScanner.Face original, Float confidence) {
			this(original.getId(), original.getTitle(), original.getDistance(), confidence, original.getLocation(), original.getCrop(), original.getExtra());
		}

		public Face(FaceDetector.Face raw, FaceScanner.Face original) {
			this(original, raw.getConfidence());
		}

		public float getConfidence() {
			return confidence;
		}
	}

	public List<Face> recognize(Bitmap input) {
		final Set<String> savedFaces = storage.getNames();
		final List<Pair<FaceDetector.Face, FaceScanner.Face>> data = detector.process(input);
		final List<Face> results = new ArrayList<>();

		for (Pair<FaceDetector.Face, FaceScanner.Face> faceFacePair : data) {
			FaceDetector.Face found = faceFacePair.first; // The generic Face object indicating where an Face is
			FaceScanner.Face scanned = faceFacePair.second; // The Face object with face-scanning data
			// Go through all saved faces and compare them with our scanned face
			for (String savedName : savedFaces) {
				float newdistance = scanned.compare(storage.get(savedName)[0]);
				// If the similarity is really low (not the same face), don't save it
				// If another known face had better similarity, don't save it
				if (newdistance < MAXIMUM_DISTANCE_TF_OD_API && newdistance < scanned.getDistance()) {
					// We have a match! Save "Face identifier" and "Distance to original values"
					scanned.addRecognitionData(savedName, newdistance);
				}
			}

			results.add(new Face(found, scanned));
		}
		return results;
	}
}
