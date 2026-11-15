package com.example.myapplication;

import android.util.JsonReader;
import android.util.JsonWriter;
import com.example.myapplication.db.Profile;
import com.example.myapplication.db.ProfileCharge;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** Explicit portable schema: no reflection, device IDs, passwords or local photo paths. */
final class ProfileTransferCodec {
    static void write(Profile p, OutputStream stream) throws IOException {
        JsonWriter w=new JsonWriter(new OutputStreamWriter(stream,StandardCharsets.UTF_8));
        w.beginObject();
        w.name("firstName").value(p.firstName);
        w.name("lastName").value(p.lastName);
        w.name("observationDate").value(p.observationDate);
        w.name("leaArrestDate").value(p.leaArrestDate);
        w.name("commitmentDate").value(p.commitmentDate);
        w.name("prisonTransferDate").value(p.prisonTransferDate);
        w.name("serviceChoices").value(p.serviceChoices);
        w.name("considerAllowancesInServiceLimit").value(p.considerAllowancesInServiceLimit);
        w.name("convicted").value(p.convicted);
        w.name("clemencyCategory").value(p.clemencyCategory);
        w.name("convictionDate").value(p.convictionDate);
        w.name("sentenceServiceStartDate").value(p.sentenceServiceStartDate);
        w.name("imprisonmentStartDate").value(p.imprisonmentStartDate);
        w.name("jailCommitmentDate").value(p.jailCommitmentDate);
        w.name("prisonCommitmentDate").value(p.prisonCommitmentDate);
        w.name("separateCustodyDates").value(p.separateCustodyDates);
        w.name("notCommittedToJail").value(p.notCommittedToJail);
        w.name("disqualifiedForCPI").value(p.disqualifiedForCPI);
        w.name("disqualifiedForTimeAllowances").value(p.disqualifiedForTimeAllowances);
        w.name("custodyTimelineConfirmed").value(p.custodyTimelineConfirmed);
        w.name("stalPercent").value(p.stalPercent);
        w.name("deductionDays").value(p.deductionDays);
        w.name("timeGapDays").value(p.timeGapDays);
        w.name("cpiCreditPercent").value(p.cpiCreditPercent);
        w.name("charges").beginArray();
        for(ProfileCharge c:p.charges){w.beginArray();
            w.value(c.minYears).value(c.minMonths).value(c.minDays).value(c.maxYears).value(c.maxMonths).value(c.maxDays);w.endArray();}
        w.endArray().name("deductions").beginArray();
        for(DeductionMonth m:p.deductionMonths){w.beginArray();w.value(m.year).value(m.month).value(m.gcta).value(m.tastm);w.endArray();}
        w.endArray().endObject();w.flush();
    }
    static Profile read(InputStream stream) throws IOException {
        JsonReader r=new JsonReader(new InputStreamReader(stream,StandardCharsets.UTF_8));
        Profile p=new Profile();Set<String> fields=new HashSet<>();
        r.beginObject();while(r.hasNext()){
            String field=r.nextName();if(!fields.add(field))throw new IOException("Repeated profile field.");
            switch(field){
                case "firstName":p.firstName=text(r);break;
                case "lastName":p.lastName=text(r);break;
                case "observationDate":p.observationDate=text(r);break;
                case "leaArrestDate":p.leaArrestDate=text(r);break;
                case "commitmentDate":p.commitmentDate=text(r);break;
                case "prisonTransferDate":p.prisonTransferDate=text(r);break;
                case "serviceChoices":p.serviceChoices=text(r);break;
                case "considerAllowancesInServiceLimit":p.considerAllowancesInServiceLimit=r.nextBoolean();break;
                case "convicted":p.convicted=r.nextBoolean();break;
                case "clemencyCategory":p.clemencyCategory=text(r);break;
                case "convictionDate":p.convictionDate=text(r);break;
                case "sentenceServiceStartDate":p.sentenceServiceStartDate=text(r);break;
                case "imprisonmentStartDate":p.imprisonmentStartDate=text(r);break;
                case "jailCommitmentDate":p.jailCommitmentDate=text(r);break;
                case "prisonCommitmentDate":p.prisonCommitmentDate=text(r);break;
                case "separateCustodyDates":p.separateCustodyDates=r.nextBoolean();break;
                case "notCommittedToJail":p.notCommittedToJail=r.nextBoolean();break;
                case "disqualifiedForCPI":p.disqualifiedForCPI=r.nextBoolean();break;
                case "disqualifiedForTimeAllowances":p.disqualifiedForTimeAllowances=r.nextBoolean();break;
                case "custodyTimelineConfirmed":p.custodyTimelineConfirmed=r.nextBoolean();break;
                case "stalPercent":p.stalPercent=r.nextInt();break;
                case "deductionDays":p.deductionDays=r.nextInt();break;
                case "timeGapDays":p.timeGapDays=r.nextInt();break;
                case "cpiCreditPercent":p.cpiCreditPercent=r.nextInt();break;
                case "charges":
                    r.beginArray();while(r.hasNext()){
                        ProfileCharge c=new ProfileCharge();r.beginArray();
                        c.minYears=r.nextInt();c.minMonths=r.nextInt();c.minDays=r.nextInt();
                        c.maxYears=r.nextInt();c.maxMonths=r.nextInt();c.maxDays=r.nextInt();r.endArray();
                        c.position=p.charges.size();p.charges.add(c);
                        if(p.charges.size()>1000)throw new IOException("Too many charges in one entry.");
                    }r.endArray();break;
                case "deductions":
                    Set<String> months=new HashSet<>();r.beginArray();while(r.hasNext()){
                        r.beginArray();int year=r.nextInt(),month=r.nextInt();boolean gcta=r.nextBoolean(),tastm=r.nextBoolean();r.endArray();
                        if(!months.add(year+"-"+month))throw new IOException("Repeated deduction month.");
                        p.deductionMonths.add(new DeductionMonth(year,month,gcta,tastm));
                        if(p.deductionMonths.size()>2400)throw new IOException("Too many deduction months in one entry.");
                    }r.endArray();break;
                default:throw new IOException("Unknown profile field. Update Laya before importing this file.");
            }
        }r.endObject();
        if(r.peek()!=android.util.JsonToken.END_DOCUMENT)throw new IOException("Unexpected data after profile.");
        // Old backups lack the new setting and receive the new enabled default.
        boolean clemencyFields=fields.contains("convicted");
        if(clemencyFields!=fields.contains("clemencyCategory"))throw new IOException("Missing clemency fields.");
        int expected=23+(fields.contains("considerAllowancesInServiceLimit")?1:0)+(clemencyFields?2:0);
        if(fields.size()!=expected)throw new IOException("Missing profile fields.");
        if(clemencyFields&&p.clemencyCategory==null)throw new IOException("Missing clemency category.");
        if(!fields.contains("charges")||!fields.contains("deductions")||p.charges.isEmpty())throw new IOException("Missing charge or deduction data.");
        if(p.firstName==null||p.firstName.trim().isEmpty()||p.lastName==null||p.lastName.trim().isEmpty())throw new IOException("Missing profile name.");
        p.charges.get(0).fillFirstFormRow(p);
        try {
            ClemencyAssessment.validate(p);
            LocalDate anchor=SentenceCalculator.requiredDate(ProfileCalculationMapper.commitmentDate(p),"Commitment date");
            SentenceCalculator.Result calculation=SentenceCalculator.calculatePreview(ProfileCalculationMapper.from(p,anchor));
            if(!calculation.available)throw new IOException("Invalid calculation data: "+calculation.problem);
            SequentialSentencePlan plan=new SequentialSentencePlan(p);
            // Retain inactive saved choices when this mode reaches the limit earlier.
            p.maximumTotalServiceDays=plan.capDays;
        }catch(IllegalArgumentException|java.time.DateTimeException|ArithmeticException e){throw new IOException("Invalid profile dates or charges.",e);}
        return p;
    }
    private static String text(JsonReader r)throws IOException{
        if(r.peek()==android.util.JsonToken.NULL){r.nextNull();return null;}
        String value=r.nextString();if(value.length()>16384)throw new IOException("Profile text is too long.");return value;
    }
}
