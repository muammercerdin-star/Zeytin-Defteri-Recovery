package com.zeytindefteri.app;

/** Formats only available weather fields, with no agronomic prescriptions. */
public final class ZeytinAssistantWeather {
    public static String answer(ZeytinAssistantCommand cmd,String garden,WeatherRepository.WeatherForecast f,String tomorrow) {
        if(f==null)return "Hava verisi alınamadı. Daha sonra tekrar deneyebilirsin.";
        String prefix=garden+": ";
        if(cmd.period.name.equals("dun") || cmd.period.name.startsWith("gecen"))return prefix+"geçmiş tarihli hava kaydı bu kaynakta bulunmuyor.";
        if(cmd.tomorrow) {
            if(cmd.text.contains("ruzgar") || cmd.farmingQuestion) {
                double max=Double.NaN;
                for(WeatherRepository.HourlyForecast h:f.hourly)if(h.timeIso.startsWith(tomorrow) && valid(h.windKmh))max=Double.isNaN(max)?h.windKmh:Math.max(max,h.windKmh);
                if(!valid(max))return prefix+"yarın için saatlik rüzgâr verisi bulunamadı. Uygulama kararı veremem; koşulları kontrol et.";
                return prefix+"yarının mevcut saatlik tahminlerinde en yüksek rüzgâr "+n(max)+" km/sa."+(cmd.farmingQuestion?" İlaçlama öncesi koşulları kontrol et; bu veri tek başına uygunluk kararı değildir.":"");
            }
            for(WeatherRepository.DailyForecast d:f.daily)if(tomorrow.equals(d.dateIso)) {
                if(cmd.text.contains("yag"))return valid(d.precipitationProbability)?prefix+"yarın yağış ihtimali %"+n(d.precipitationProbability)+". Bu bir tahmindir.":prefix+"yarın için yağış olasılığı verisi bulunamadı.";
                String s="Yarın";
                if(valid(d.maxTemperatureC))s+=" en yüksek "+n(d.maxTemperatureC)+"°C";
                if(valid(d.minTemperatureC))s+=", en düşük "+n(d.minTemperatureC)+"°C";
                if(valid(d.precipitationProbability))s+=", yağış ihtimali %"+n(d.precipitationProbability);
                return s.equals("Yarın")?prefix+"yarın için yeterli hava verisi bulunamadı.":prefix+s+".";
            }
            return prefix+"yarın için hava tahmini bulunamadı.";
        }
        if(cmd.text.contains("5 gun")||cmd.text.contains("bu hafta")){
            if(f.daily.isEmpty())return prefix+"günlük tahmin bulunamadı.";
            StringBuilder out=new StringBuilder(prefix+"Mevcut 5 günlük tahmin: ");int count=0;
            for(WeatherRepository.DailyForecast d:f.daily){if(count++==5)break;out.append(d.dateIso).append(": ");
                if(valid(d.maxTemperatureC))out.append("en yüksek ").append(n(d.maxTemperatureC)).append("°C; ");
                if(valid(d.minTemperatureC))out.append("en düşük ").append(n(d.minTemperatureC)).append("°C; ");
                out.append(valid(d.precipitationProbability)?"yağış %"+n(d.precipitationProbability):"yağış verisi yok").append(". ");
            }return out.toString();
        }
        if(cmd.text.contains("yag")||cmd.text.contains("en yuksek")||cmd.text.contains("en dusuk")){
            java.text.SimpleDateFormat df=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US);df.setTimeZone(java.util.TimeZone.getTimeZone("Europe/Istanbul"));String today=df.format(new java.util.Date());
            for(WeatherRepository.DailyForecast d:f.daily)if(today.equals(d.dateIso)){
                double value=cmd.text.contains("yag")?d.precipitationProbability:cmd.text.contains("en dusuk")?d.minTemperatureC:d.maxTemperatureC;
                return prefix+(valid(value)?(cmd.text.contains("yag")?"bugün yağış ihtimali %"+n(value):"bugün "+(cmd.text.contains("en dusuk")?"en düşük ":"en yüksek ")+n(value)+"°C")+".":"istenen günlük hava verisi bulunamadı.");
            }return prefix+"bugün için günlük tahmin bulunamadı.";
        }
        WeatherRepository.WeatherSnapshot w=f.current;
        if(w==null)return prefix+"güncel hava verisi bulunamadı.";
        if(cmd.farmingQuestion)return prefix+"mevcut rüzgâr "+(valid(w.windKmh)?n(w.windKmh)+" km/sa":"verisi bulunamadı")+". Uygulama kararı veremem; bahçedeki koşulları kontrol et.";
        if(cmd.text.contains("nem"))return prefix+(valid(w.humidity)?"şu an nem %"+n(w.humidity)+".":"nem verisi bulunamadı.");
        if(cmd.text.contains("ruzgar"))return prefix+(valid(w.windKmh)?"şu an rüzgâr "+n(w.windKmh)+" km/sa.":"rüzgâr verisi bulunamadı.");
        return prefix+(valid(w.temperatureC)?"şu an "+n(w.temperatureC)+"°C, "+w.condition+".":"sıcaklık verisi bulunamadı.");
    }
    private static boolean valid(double d){return !Double.isNaN(d)&&!Double.isInfinite(d);}
    private static String n(double d){return String.valueOf(Math.round(d));}
}
