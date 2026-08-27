package kr.co.ninetyseconds.recommendation.server.event

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SyncRecommendationEvents(private val store: RecommendationEventStore) {
    @Transactional
    operator fun invoke(events: List<RecommendationEvent>): List<String> =
        events.onEach(store::appendIfAbsent).map { it.eventId.toString() }
}
