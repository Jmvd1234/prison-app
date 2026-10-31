package com.example.myapplication;

import com.example.myapplication.db.Profile;
import com.example.myapplication.db.ProfileCharge;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * User-requested sequential simulation. The cap limits actual custody, after each
 * charge's allowances, and is deliberately separate from the aggregate calculator.
 * Choices retain the previous endpoint; every new charge keeps its full range. Dates are
 * recomputed from those choices when custody inputs or monthly deductions change.
 */
public final class SequentialSentencePlan {
    public static final class Stage {
        public final int number;
        public final ProfileCharge charge;
        public final LocalDate start, minimum, maximum;
        public final boolean previousMaximum, previousCustom;
        private final long consumedCredit;
        private Stage(int number, ProfileCharge charge, LocalDate start, boolean previousMaximum, boolean previousCustom, long consumedCredit, LocalDate minimum, LocalDate maximum) {
            this.number=number; this.charge=charge; this.start=start; this.previousMaximum=previousMaximum; this.previousCustom=previousCustom;
            this.minimum=minimum; this.maximum=maximum; this.consumedCredit=consumedCredit;
        }
    }

    public final Profile profile;
    public final List<ProfileCharge> ordered;
    public final List<Stage> stages;
    public final LocalDate start, commitment, capDate;
    public final long capDays;
    public final String choices;

    public SequentialSentencePlan(Profile profile) {
        this.profile=profile;
        commitment=SentenceCalculator.requiredDate(ProfileCalculationMapper.commitmentDate(profile), "Commitment date");
        SentenceCalculator.Input input=ProfileCalculationMapper.from(profile, LocalDate.now());
        LocalDate arrest=SentenceCalculator.optionalDate(profile.leaArrestDate,"LEA arrest date");
        LocalDate jail=SentenceCalculator.optionalDate(input.jailBeforePrisonDate,"Jail commitment date");
        start=arrest!=null?arrest:jail!=null?jail:commitment;
        ordered=Collections.unmodifiableList(orderedCharges(profile));
        if(ordered.isEmpty())throw new IllegalArgumentException("Enter at least one charge.");
        // Validate all terms, including pending ones, before a cap can hide an error.
        for(ProfileCharge c:ordered) {
            if(term(c,commitment,false)>term(c,commitment,true))
                throw new IllegalArgumentException("Minimum penalty exceeds maximum penalty.");
        }
        capDays=maximumServiceDays(profile);
        // Gaps do not count as actual custody; allowances never move this marker.
        capDate=start.plusDays(Math.addExact(capDays,profile.timeGapDays));
        choices=profile.serviceChoices==null?"":profile.serviceChoices;
        List<String> route=decodeChoices(choices);
        if(route.size()>=ordered.size())throw new IllegalArgumentException("Saved charge choices exceed the charge count.");
        List<Stage> built=new ArrayList<>();
        LocalDate nextStart=start;
        for(int i=0;i<=route.size();i++) {
            boolean previousMaximum=false, previousCustom=false;
            long consumedCredit=0;
            if(i>0) {
                String code=route.get(i-1);
                previousCustom=code.startsWith("@");
                previousMaximum=code.equals("2")||code.equals("3");
                Stage previous=built.get(i-1);
                nextStart=previousCustom?LocalDate.parse(code.substring(1)):
                        previousMaximum?previous.maximum:previous.minimum;
                if(nextStart.isBefore(previous.start)||nextStart.isAfter(previous.maximum))
                    throw new IllegalArgumentException("Custom end date must be between the charge start and maximum release date.");
                SentenceCalculator.Result atBoundary=raw(previous,nextStart);
                if(previousCustom) {
                    // A custom handoff closes the earlier charge with precisely the credit
                    // earned by that date. Do not charge its unserved nominal term again.
                    consumedCredit=atBoundary.actualPreventiveDays+atBoundary.actualServiceDays
                            +atBoundary.netAllowanceDays-profile.timeGapDays-atBoundary.stalDays;
                } else {
                    long servedTerm=term(previous.charge,previous.number==1?commitment:previous.start,previousMaximum);
                    // Preserve surplus anniversary credit for ordinary min/max handoffs.
                    consumedCredit=previous.consumedCredit+servedTerm-atBoundary.stalDays;
                }
                if(!previous.maximum.isBefore(capDate))break; // No charge can start beyond the custody limit.
            }
            Stage pending=new Stage(i+1,ordered.get(i),nextStart,previousMaximum,previousCustom,consumedCredit,null,null);
            LocalDate minimum=completion(pending,true), maximum=completion(pending,false);
            built.add(new Stage(i+1,pending.charge,nextStart,previousMaximum,previousCustom,consumedCredit,minimum,maximum));
        }
        stages=Collections.unmodifiableList(built);
    }

    public Stage stageAt(LocalDate date){
        Stage result=stages.get(0);
        for(Stage stage:stages)if(!date.isBefore(stage.start))result=stage;
        return result;
    }
    public SentenceCalculator.Result calculateAtDate(LocalDate date){return calculate(stageAt(date),date);}
    public static SentenceCalculator.Result initialResult(Profile profile,LocalDate date){
        SentenceCalculator.Input input=ProfileCalculationMapper.from(profile,date);
        SentenceCalculator.Result validated=SentenceCalculator.calculate(input);
        if(!validated.available||profile.charges.size()<2)return validated;
        input.charges=Collections.singletonList(orderedCharges(profile).get(0).toPenalty());
        SentenceCalculator.Result first=SentenceCalculator.calculate(input);
        LocalDate anchor=SentenceCalculator.requiredDate(input.commitmentDate,"Commitment date");
        LocalDate arrest=SentenceCalculator.optionalDate(input.leaArrestDate,"LEA arrest date");
        LocalDate jail=SentenceCalculator.optionalDate(input.jailBeforePrisonDate,"Jail commitment date");
        LocalDate start=arrest!=null?arrest:jail!=null?jail:anchor;
        long remaining=ChronoUnit.DAYS.between(date,start.plusDays(maximumServiceDays(profile)+profile.timeGapDays));
        return first.withBalances(Math.min(first.minRemainingDays,remaining),Math.min(first.maxRemainingDays,remaining),
                first.minSentenceDays,first.maxSentenceDays,profile.charges.size());
    }
    public Stage active(){return stages.get(stages.size()-1);}
    public boolean hasNext(){return active().number<ordered.size()&&active().maximum.isBefore(capDate);}
    public boolean hasNext(LocalDate observation){return hasNext()&&!limitRelevant(observation);}
    public boolean limitRelevant(LocalDate observation){
        if(!observation.isBefore(capDate))return true;
        // Before the cap date, only a charge actually constrained by the cap
        // at this observation should expose it.
        if(!observation.isBefore(active().maximum))return !active().maximum.isBefore(capDate);
        return calculate(active(),observation,false).maxRemainingDays>=ChronoUnit.DAYS.between(observation,capDate);
    }
    public LocalDate overdueStart(LocalDate observation){
        if(!active().maximum.isBefore(capDate))return capDate;
        return finalCharge()?active().maximum:!observation.isBefore(capDate)?capDate:null;
    }
    public String appendChoice(boolean fromMaximum,LocalDate observation){
        if(limitRelevant(observation))throw new IllegalArgumentException("actual custody limit reached.");
        return appendChoice(false,fromMaximum);
    }
    public boolean finalCharge(){return active().number==ordered.size();}
    public String appendChoice(boolean ignoredNextMaximum,boolean fromMaximum) {
        if(!hasNext())throw new IllegalArgumentException(!active().maximum.isBefore(capDate)?"actual custody limit reached.":"No further charge can be served.");
        LocalDate boundary=fromMaximum?active().maximum:active().minimum;
        if(!boundary.isBefore(capDate))throw new IllegalArgumentException("The custody limit has already been reached at this endpoint.");
        return appendToken(fromMaximum?"2":"0");
    }

    public String appendCustomDate(LocalDate date, LocalDate observation) {
        if(!hasNext()||limitRelevant(observation))throw new IllegalArgumentException("actual custody limit reached.");
        if(date==null||date.isBefore(active().start)||date.isAfter(active().maximum))
            throw new IllegalArgumentException("Choose a date from the charge start through its maximum release date.");
        return appendToken("@"+date);
    }
    private String appendToken(String token) {
        List<String> route=new ArrayList<>(decodeChoices(choices).subList(0,active().number-1));
        route.add(token);return encodeChoices(route);
    }
    public String choicesBefore(int chargeNumber) {
        return encodeChoices(decodeChoices(choices).subList(0,chargeNumber-2));
    }
    static List<String> decodeChoices(String value) {
        List<String> route=new ArrayList<>();
        if(value==null||value.isEmpty())return route;
        if(value.startsWith("v2:"))Collections.addAll(route,value.substring(3).split("\\|",-1));
        else for(int i=0;i<value.length();i++)route.add(String.valueOf(value.charAt(i)));
        for(String token:route) {
            if(token.matches("[0-3]"))continue;
            if(!token.startsWith("@"))throw new IllegalArgumentException("Invalid saved charge choice.");
            LocalDate.parse(token.substring(1));
        }
        return route;
    }
    private static String encodeChoices(List<String> route) {
        boolean custom=false;for(String token:route)if(token.startsWith("@"))custom=true;
        return custom?"v2:"+String.join("|",route):String.join("",route);
    }

    public SentenceCalculator.Result calculate(LocalDate date){return calculate(active(),date);}
    private SentenceCalculator.Result calculate(Stage stage,LocalDate date) {
        return calculate(stage,date,true);
    }
    private SentenceCalculator.Result calculate(Stage stage,LocalDate date,boolean capped) {
        SentenceCalculator.Result current=raw(stage,date);
        if(!current.available)throw new IllegalArgumentException(current.problem);
        long minTerm=term(stage.charge,stage.number==1?commitment:stage.start,false);
        long maxTerm=term(stage.charge,stage.number==1?commitment:stage.start,true);
        long earned=current.actualPreventiveDays+current.actualServiceDays+current.netAllowanceDays-profile.timeGapDays;
        earned-=stage.consumedCredit;
        long capRemaining=capped?ChronoUnit.DAYS.between(date,capDate):Long.MAX_VALUE;
        return current.withBalances(Math.min(minTerm-earned,capRemaining),
                Math.min(maxTerm-earned,capRemaining),minTerm,maxTerm,ordered.size());
    }

    private SentenceCalculator.Result raw(Stage stage,LocalDate date) {
        SentenceCalculator.Input input=ProfileCalculationMapper.from(profile,date);
        long min=term(stage.charge,stage.number==1?commitment:stage.start,false);
        long max=term(stage.charge,stage.number==1?commitment:stage.start,true);
        input.charges=Collections.singletonList(new SentenceCalculator.Penalty(0,0,Math.toIntExact(min),0,0,Math.toIntExact(max)));
        return SentenceCalculator.calculatePreview(input);
    }

    private LocalDate completion(Stage stage,boolean minimum) {
        // Monthly deductions may make balances non-monotonic. A daily scan returns
        // the first actual crossing even when credits are withheld the next month.
        LocalDate end=capDate;
        if(ChronoUnit.DAYS.between(stage.start,end)>200*366L)
            throw new IllegalArgumentException("Service exceeds the supported projection range.");
        for(LocalDate date=stage.start;!date.isAfter(end);date=date.plusDays(1)) {
            if(Thread.currentThread().isInterrupted())throw new IllegalStateException("Projection cancelled.");
            SentenceCalculator.Result result=calculate(stage,date);
            if((minimum?result.minRemainingDays:result.maxRemainingDays)<=0)return date;
        }
        return end;
    }

    public SentenceProjection.Range range(LocalDate today,boolean overview) {
        Stage stage=active();
        // Keep the original origin and every earlier extent. Adding a charge
        // must not erase history or shorten the track, even after a minimum handoff.
        LocalDate end=today;
        for(Stage prior:stages){
            LocalDate estimate=prior.start.plusDays(Math.max(0,calculate(prior,prior.start).maxRemainingDays));
            LocalDate extent=estimate.isAfter(prior.maximum)?estimate:prior.maximum;
            extent=extent.plusYears(1); // Allow an observation beyond release/the hard cap.
            if(extent.isAfter(end))end=extent;
        }
        if(!end.isAfter(start))end=start.plusDays(1);
        List<SentenceProjection.Milestone> marks=new ArrayList<>();
        if(overview) {
            LocalDate arrest=SentenceCalculator.optionalDate(profile.leaArrestDate,"LEA arrest date");
            if(arrest!=null)marks.add(new SentenceProjection.Milestone(SentenceProjection.Kind.LEA_ARREST,arrest));
            SentenceCalculator.Input input=ProfileCalculationMapper.from(profile,today);
            LocalDate jail=SentenceCalculator.optionalDate(input.jailBeforePrisonDate,"Jail commitment date");
            if(jail!=null)marks.add(new SentenceProjection.Milestone(SentenceProjection.Kind.JAIL_COMMITMENT,jail));
            marks.add(new SentenceProjection.Milestone(SentenceCalculator.usesPrisonCommitment(profile.notCommittedToJail,profile.disqualifiedForCPI)?SentenceProjection.Kind.PRISON_COMMITMENT:SentenceProjection.Kind.JAIL_COMMITMENT,commitment));
            LocalDate transfer=SentenceCalculator.optionalDate(input.prisonTransferDate,"Prison commitment date");
            if(transfer!=null)marks.add(new SentenceProjection.Milestone(SentenceProjection.Kind.PRISON_COMMITMENT,transfer));
        }
        marks.add(new SentenceProjection.Milestone(SentenceProjection.Kind.MINIMUM_ZERO,stage.minimum));
        marks.add(new SentenceProjection.Milestone(SentenceProjection.Kind.MAXIMUM_ZERO,stage.maximum));
        marks.add(new SentenceProjection.Milestone(SentenceProjection.Kind.TODAY,today));
        return new SentenceProjection.Range(start,commitment,stage.minimum,stage.maximum,end,marks);
    }

    public static List<ProfileCharge> orderedCharges(Profile profile) {
        List<ProfileCharge> result=new ArrayList<>();
        if(profile.charges==null||profile.charges.isEmpty())result.add(ProfileCharge.firstFormRow(profile));
        else for(ProfileCharge c:profile.charges) {
            if(c==null)throw new IllegalArgumentException("A charge cannot be empty.");
            result.add(c.copyFor(c.profileID,c.position));
        }
        LocalDate anchor=SentenceCalculator.requiredDate(ProfileCalculationMapper.commitmentDate(profile),"Commitment date");
        // Stable order for identical terms; maximum first, then minimum as tie-break.
        result.sort(Comparator.<ProfileCharge>comparingLong(c->term(c,anchor,true)).reversed()
                .thenComparing(Comparator.<ProfileCharge>comparingLong(c->term(c,anchor,false)).reversed()));
        return result;
    }
    public static long maximumServiceDays(Profile profile) {
        LocalDate anchor=SentenceCalculator.requiredDate(ProfileCalculationMapper.commitmentDate(profile),"Commitment date");
        List<ProfileCharge> charges=orderedCharges(profile);
        long longest=term(charges.get(0),anchor,true);
        if(charges.size()==1)return longest; // Retain sole-charge behavior, including long terms.
        return Math.min(Math.multiplyExact(longest,3),ChronoUnit.DAYS.between(anchor,anchor.plusYears(40)));
    }
    public static long term(ProfileCharge charge,LocalDate anchor,boolean maximum) {
        return SentenceCalculator.sentenceDays(anchor,maximum?charge.maxYears:charge.minYears,
                maximum?charge.maxMonths:charge.minMonths,maximum?charge.maxDays:charge.minDays);
    }
    public static String signature(Profile profile) {
        StringBuilder value=new StringBuilder(String.valueOf(ProfileCalculationMapper.commitmentDate(profile)));
        for(ProfileCharge c:profile.charges)value.append('|').append(c.minYears).append(',').append(c.minMonths).append(',').append(c.minDays).append(',').append(c.maxYears).append(',').append(c.maxMonths).append(',').append(c.maxDays);
        return value.toString();
    }
}
