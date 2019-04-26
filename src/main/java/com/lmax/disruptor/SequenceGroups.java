package com.lmax.disruptor;

import static java.util.Arrays.copyOf;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * 提供用于管理{@link SequenceGroup}对象的静态方法
 */
class SequenceGroups {
	static <T> void addSequences(final T holder, final AtomicReferenceFieldUpdater<T, Sequence[]> updater,
			final Cursored cursor, final Sequence... sequencesToAdd) {
		long cursorSequence;
		Sequence[] updatedSequences;
		Sequence[] currentSequences;

		do {
			currentSequences = updater.get(holder);
			updatedSequences = copyOf(currentSequences, currentSequences.length + sequencesToAdd.length);
			cursorSequence = cursor.getCursor();

			int index = currentSequences.length;
			for (Sequence sequence : sequencesToAdd) {
				sequence.set(cursorSequence);
				updatedSequences[index++] = sequence;
			}
		} while (!updater.compareAndSet(holder, currentSequences, updatedSequences));

		cursorSequence = cursor.getCursor();
		for (Sequence sequence : sequencesToAdd) {
			sequence.set(cursorSequence);
		}
	}

	static <T> boolean removeSequence(final T holder, final AtomicReferenceFieldUpdater<T, Sequence[]> sequenceUpdater,
			final Sequence sequence) {
		int numToRemove;
		Sequence[] oldSequences;
		Sequence[] newSequences;

		do {
			oldSequences = sequenceUpdater.get(holder);

			numToRemove = countMatching(oldSequences, sequence);

			if (0 == numToRemove) {
				break;
			}

			final int oldSize = oldSequences.length;
			newSequences = new Sequence[oldSize - numToRemove];

			for (int i = 0, pos = 0; i < oldSize; i++) {
				final Sequence testSequence = oldSequences[i];
				if (sequence != testSequence) {
					newSequences[pos++] = testSequence;
				}
			}
		} while (!sequenceUpdater.compareAndSet(holder, oldSequences, newSequences));

		return numToRemove != 0;
	}

	private static <T> int countMatching(T[] values, final T toMatch) {
		int numToRemove = 0;
		for (T value : values) {
			// 具体使用身份
			if (value == toMatch) {
				numToRemove++;
			}
		}
		return numToRemove;
	}
}
