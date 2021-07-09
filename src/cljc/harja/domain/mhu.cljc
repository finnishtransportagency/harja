(ns harja.domain.mhu)

(defn- key-from-val [m v]
  (some (fn [[k v_]]
          (if (map? v_)
            (when (key-from-val v_ v)
              k)
            (when (= v v_)
              k)))
        m))
; nämä on jotain tunnisteita, mutta mitä?
(def toimenpide-avain->toimenpide
  {:paallystepaikkaukset "20107"
   :mhu-yllapito "20191"
   :talvihoito "23104"
   :liikenneympariston-hoito "23116"
   :sorateiden-hoito "23124"
   :mhu-korvausinvestointi "14301"
   :mhu-johto "23151"})

; nämä viittaavat toimenpidekoodien yksilöiviin tunnisteisiin, sillä nimet voivat muuttua tai vanheta (?), niin näillä sitten löytyvät ne kurantit toimenpidekoodit aina tarvittaessa
(def hoidonjohtopalkkio-tunniste "53647ad8-0632-4dd3-8302-8dfae09908c8")
(def toimistokulut-tunniste "8376d9c4-3daf-4815-973d-cd95ca3bb388")
(def kolmansien-osapuolten-vahingot-talvihoito-tunniste "49b7388b-419c-47fa-9b1b-3797f1fab21d")
(def kolmansien-osapuolten-vahingot-liikenneympariston-hoito-tunniste "63a2585b-5597-43ea-945c-1b25b16a06e2")
(def kolmansien-osapuolten-vahingot-sorateiden-hoito-tunniste "b3a7a210-4ba6-4555-905c-fef7308dc5ec")
(def akilliset-hoitotyot-talvihoito-tunniste "1f12fe16-375e-49bf-9a95-4560326ce6cf")
(def akilliset-hoitotyot-liikenneympariston-hoito-tunniste "1ed5d0bb-13c7-4f52-91ee-5051bb0fd974")
(def akilliset-hoitotyot-sorateiden-hoito-tunniste "d373c08b-32eb-4ac2-b817-04106b862fb1")


; sama kuin ylempänä, mutta kohdistuu tehtäväryhmiin
(def erillishankinnat-tunniste "37d3752c-9951-47ad-a463-c1704cf22f4c")
(def rahavaraus-lupaukseen-1-tunniste "0e78b556-74ee-437f-ac67-7a03381c64f6")
(def johto-ja-hallintokorvaukset-tunniste "a6614475-1950-4a61-82c6-fda0fd19bb54")

(defn toimenpide->toimenpide-avain [v]
  (key-from-val toimenpide-avain->toimenpide v))

(def tallennettava-asia->tyyppi
  {:hoidonjohtopalkkio "laskutettava-tyo"
   :toimistokulut "laskutettava-tyo"
   :erillishankinnat "laskutettava-tyo"
   :rahavaraus-lupaukseen-1 "muut-rahavaraukset"
   :kolmansien-osapuolten-aiheuttamat-vahingot "vahinkojen-korjaukset"
   :akilliset-hoitotyot "akillinen-hoitotyo"
   :toimenpiteen-maaramitattavat-tyot "laskutettava-tyo"
   :tilaajan-varaukset "laskutettava-tyo"})

(defn tyyppi->tallennettava-asia [v]
  (key-from-val tallennettava-asia->tyyppi v))


(def tallennettava-asia->tehtava
  {:hoidonjohtopalkkio hoidonjohtopalkkio-tunniste
   :toimistokulut toimistokulut-tunniste
   :kolmansien-osapuolten-aiheuttamat-vahingot {:talvihoito kolmansien-osapuolten-vahingot-talvihoito-tunniste
                                                :liikenneympariston-hoito kolmansien-osapuolten-vahingot-liikenneympariston-hoito-tunniste
                                                :sorateiden-hoito kolmansien-osapuolten-vahingot-sorateiden-hoito-tunniste}
   :akilliset-hoitotyot {:talvihoito akilliset-hoitotyot-talvihoito-tunniste
                         :liikenneympariston-hoito akilliset-hoitotyot-liikenneympariston-hoito-tunniste
                         :sorateiden-hoito akilliset-hoitotyot-sorateiden-hoito-tunniste}})

(defn tehtava->tallennettava-asia [v]
  (key-from-val tallennettava-asia->tehtava v))

(def tallennettava-asia->tehtavaryhma
  {:erillishankinnat        erillishankinnat-tunniste
   :rahavaraus-lupaukseen-1 rahavaraus-lupaukseen-1-tunniste ;; Käsitteellisesti :tilaajan-varaukset = :rahavaraus-lupaukseen-1. En uskalla/ehdi uudelleennimetä avainta tässä vaiheessa. ML.
   :tilaajan-varaukset      johto-ja-hallintokorvaukset-tunniste}) ;; Kyseessä on johto-ja-hallintokorvaus-tehtäväryhmä.

(defn tehtavaryhma->tallennettava-asia [v]
  (key-from-val tallennettava-asia->tehtavaryhma v))