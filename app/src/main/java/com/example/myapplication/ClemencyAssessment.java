package com.example.myapplication;

import com.example.myapplication.db.Profile;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Service-period reminder, not an eligibility decision or a new release/custody limit. */
public final class ClemencyAssessment {
    public final ClemencyCategory category;
    public final LocalDate reviewDate;
    public final long requiredDays;

    private ClemencyAssessment(ClemencyCategory category, LocalDate date, long days) {
        this.category = category; reviewDate = date; requiredDays = days;
    }

    public static void validate(Profile profile) {
        ClemencyCategory category = ClemencyCategory.parse(profile.clemencyCategory);
        if (!profile.convicted && category != ClemencyCategory.NONE)
            throw new IllegalArgumentException("Confirm conviction before selecting a clemency category.");
    }

    public static ClemencyAssessment create(Profile profile, SequentialSentencePlan plan) {
        validate(profile);
        ClemencyCategory category = ClemencyCategory.parse(profile.clemencyCategory);
        if (!profile.convicted || category == ClemencyCategory.NONE)
            return new ClemencyAssessment(ClemencyCategory.NONE, null, 0);
        // Use the same custody/service counter as the Actual Time Served boxes.
        // Convicted is a record confirmation, not a substitute date or a new clock.
        LocalDate anchor = SentenceCalculator.requiredDate(ProfileCalculationMapper.commitmentDate(profile), "Commitment date");
        SentenceCalculator.Result initial = at(profile, plan, anchor);
        LocalDate start = initial.custodyStart;
        long required = ChronoUnit.DAYS.between(start, start.plusYears(category.years));
        if (!profile.considerAllowancesInServiceLimit)
            return new ClemencyAssessment(category, start.plusDays(Math.addExact(required, profile.timeGapDays)), required);
        // Check daily, including anniversary credit jumps and withheld months. The
        // first crossing stays fixed, just like the app's existing service-limit date.
        LocalDate end = start.plusYears(200);
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Projection cancelled.");
            if (at(profile, plan, date).timeServedWithAllowancesDays >= required)
                return new ClemencyAssessment(category, date, required);
        }
        throw new IllegalArgumentException("Clemency review exceeds the supported projection range.");
    }

    private static SentenceCalculator.Result at(Profile p, SequentialSentencePlan plan, LocalDate date) {
        SentenceCalculator.Result result = plan == null
                ? SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(p, date))
                : plan.calculateAtDate(date);
        if (!result.available) throw new IllegalArgumentException(result.problem);
        return result;
    }

    public boolean reached(LocalDate observation) {
        return reviewDate != null && !observation.isBefore(reviewDate);
    }

    public boolean recommend(LocalDate observation, SentenceCalculator.Result result, SequentialSentencePlan plan) {
        if (!result.available || !reached(observation)) return false;
        // A final release or a reached hard cap takes priority over clemency review.
        if (plan == null) return result.maxRemainingDays > 0;
        return observation.isBefore(plan.capDate)
                && (plan.stageAt(observation).number < plan.ordered.size() || result.maxRemainingDays > 0);
    }

    public SentenceProjection.Range addTo(SentenceProjection.Range range) {
        if (reviewDate == null || range == null) return range;
        List<SentenceProjection.Milestone> marks = new ArrayList<>(range.milestones);
        marks.add(new SentenceProjection.Milestone(SentenceProjection.Kind.CLEMENCY_REVIEW, reviewDate));
        LocalDate end = range.end.isBefore(reviewDate) ? reviewDate : range.end;
        return new SentenceProjection.Range(range.start, range.commitment, range.minimumCompletion,
                range.maximumCompletion, end, marks);
    }
}
