package cat.subi.itaca.training.adapter.out.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface WorkoutRepository : JpaRepository<WorkoutEntity, Long> {
    fun findFirstByCompletedFalseOrderByDateDescIdDesc(): WorkoutEntity?
}

interface SetRepository : JpaRepository<SetEntity, Long> {
    fun countByWorkoutId(workoutId: Long): Int
}
