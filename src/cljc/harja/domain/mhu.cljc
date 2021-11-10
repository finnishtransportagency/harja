(ns harja.domain.mhu
  (:require
    #?(:clj [slingshot.slingshot :refer [throw+]])))

(defn- key-from-val [m v]
  (some (fn [[k v_]]
          (if (map? v_)
            (when (key-from-val v_ v)
              k)
            (when (= v v_)
              k)))
        m))

(defn maksukausi->kuukaudet-range
  "Johto-ja hallintokorvausten toimenkuvan maksukausia kuukausina (range)."
  [maksukausi]
  (case maksukausi
    :molemmat (vec (range 1 13))
    :talvi (vec (concat (range 1 5) (range 10 13)))
    :kesa (vec (range 5 10))
    (vec (range 1 13))))

(defn maksukausi->kuukausien-lkm
  "Maksukausi mapattuna kuukausien lukumääräksi."
  [maksukausi]

  (case maksukausi
    :kesa 5
    :talvi 7
    :molemmat 12
    nil))

(defn kuukausien-lkm->maksukausi
  "Kuukausien lukumäärä mapattuna maksukaudeksi."
  [maksukausi]

  (case maksukausi
     5 :kesa
     7 :talvi
     12 :molemmat
    nil))


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
(def kolmansien-osapuolten-vahingot-liikenneympariston-hoito-tunniste "63a2585b-5597-43ea-945c-1b25b16a06e2")

;; TODO: "Vahinkojen korvaukset", eli tässä "kolmansien osapuolten vahingot" tyyppisiä rahavarauksia
;;       ei voi enää kirjata talvihoito ja soreiteiden hoito toimenpiteille.
;;      Näille ei liene enää käyttöä täällä, mutta pidetään vielä jonkin aikaa mukana, jos tulee tarve siirrellä vanhaa dataa tietokannassa.
(def kolmansien-osapuolten-vahingot-talvihoito-tunniste "49b7388b-419c-47fa-9b1b-3797f1fab21d")
(def kolmansien-osapuolten-vahingot-sorateiden-hoito-tunniste "b3a7a210-4ba6-4555-905c-fef7308dc5ec")


(def akilliset-hoitotyot-liikenneympariston-hoito-tunniste "1ed5d0bb-13c7-4f52-91ee-5051bb0fd974")

;; TODO: Akillisiä hoitotöitä ei voi enää kirjata talvihoito ja soreiteiden hoito toimenpiteille.
;;      Näille ei liene enää käyttöä täällä, mutta pidetään vielä jonkin aikaa mukana, jos tulee tarve siirrellä vanhaa dataa tietokannassa.
(def akilliset-hoitotyot-talvihoito-tunniste "1f12fe16-375e-49bf-9a95-4560326ce6cf")
(def akilliset-hoitotyot-sorateiden-hoito-tunniste "d373c08b-32eb-4ac2-b817-04106b862fb1")


; sama kuin ylempänä, mutta kohdistuu tehtäväryhmiin
(def erillishankinnat-tunniste "37d3752c-9951-47ad-a463-c1704cf22f4c")
(def rahavaraus-lupaukseen-1-tunniste "0e78b556-74ee-437f-ac67-7a03381c64f6")
(def johto-ja-hallintokorvaukset-tunniste "a6614475-1950-4a61-82c6-fda0fd19bb54")

(defn toimenpide->toimenpide-avain [v]
  (key-from-val toimenpide-avain->toimenpide v))

(def toimenpiteen-rahavarausten-tyypit
  "Kustannusarvioitujen töiden tyypit, jotka liittyvät pelkästään toimenpiteiden rahavarauksiin."

  #{"akillinen-hoitotyo" "vahinkojen-korjaukset" "muut-rahavaraukset"})

(def tallennettava-asia->tyyppi
  "Nämä liittyy pelkästään kustannusarvioituihin töihin."
  {:hoidonjohtopalkkio "laskutettava-tyo"
   :toimistokulut "laskutettava-tyo"
   :erillishankinnat "laskutettava-tyo"
   :rahavaraus-lupaukseen-1 "muut-rahavaraukset"
   :kolmansien-osapuolten-aiheuttamat-vahingot "vahinkojen-korjaukset"
   :akilliset-hoitotyot "akillinen-hoitotyo"
   :toimenpiteen-maaramitattavat-tyot "laskutettava-tyo"
   :tilaajan-varaukset "laskutettava-tyo"})

(defn kustannusarvioitu-tyo-laske-indeksikorjaus?
  "Tämä liittyy kustannusarvioitujen töiden tallennettaviin asioihin.
  Palauttaa booleanin, jonka perusteella päätetään lasketaanko tallennettavalle asialle automaattisesti indeksikorjaus
  vai ei."
  [tallennettava-asia]
  {:pre [(keyword? tallennettava-asia)]}
  ;; Ainoastaan "tilaajan varaukset" on tällä hetkellä sellainen "tallennettava asia", jolle ei lasketa indeksikorjausta.
  (not (= :tilaajan-varaukset tallennettava-asia)))

(defn tyyppi->tallennettava-asia [v]
  (key-from-val tallennettava-asia->tyyppi v))


(def tallennettava-asia->tehtava
  {:hoidonjohtopalkkio hoidonjohtopalkkio-tunniste
   :toimistokulut toimistokulut-tunniste
   ;; Kolmansien osapuolten aiheuttamat vahingot = "vahinkojen korjaukset"
   :kolmansien-osapuolten-aiheuttamat-vahingot {:liikenneympariston-hoito kolmansien-osapuolten-vahingot-liikenneympariston-hoito-tunniste}
   :akilliset-hoitotyot {:liikenneympariston-hoito akilliset-hoitotyot-liikenneympariston-hoito-tunniste}})

(defn tehtava->tallennettava-asia [v]
  (key-from-val tallennettava-asia->tehtava v))

(def tallennettava-asia->tehtavaryhma
  {:erillishankinnat        erillishankinnat-tunniste
   :rahavaraus-lupaukseen-1 rahavaraus-lupaukseen-1-tunniste ;; Käsitteellisesti :tilaajan-varaukset = :rahavaraus-lupaukseen-1. En uskalla/ehdi uudelleennimetä avainta tässä vaiheessa. ML.
   :tilaajan-varaukset      johto-ja-hallintokorvaukset-tunniste}) ;; Kyseessä on johto-ja-hallintokorvaus-tehtäväryhmä.

(defn tehtavaryhma->tallennettava-asia [v]
  (key-from-val tallennettava-asia->tehtavaryhma v))


;; ---

(def suuunnitelman-osiot
  #{:johto-ja-hallintokorvaus
    :erillishankinnat
    :hoidonjohtopalkkio
    :hankintakustannukset

    :tavoite-ja-kattohinta

    ;; Ei vaikuta tavoite- ja kattohintaan
    :tilaajan-rahavaraukset})

(def suunnitelman-osio->taulutyypit
  "Mappaa suunnitelman osion siihen liittyvään tietokantatauluun."
  {:johto-ja-hallintokorvaus #{:johto-ja-hallintokorvaus :kustannusarvioitu-tyo}
   :erillishankinnat #{:kustannusarvioitu-tyo}
   :hoidonjohtopalkkio #{:kustannusarvioitu-tyo}
   :hankintakustannukset #{:kiinteahintainen-tyo :kustannusarvioitu-tyo}
   :tavoite-ja-kattohinta #{:urakka-tavoite}
   :tilaajan-rahavaraukset #{:kustannusarvioitu-tyo}})

(def osioiden-riippuvuudet
  "Kuvaa osioiden väliset riippuuvet vahvistuksen suhteen.
  Asetus 'kumoa-osiot' tarkoittaa osioita, joiden vahvistus kumotaan lisäksi sen jälkeen kun käsiteltävä osio on kumottu.
  Asetus 'vahvistus-vaadittu-osiot' tarkoittaa, että nämä osiot täytyy olla vahvistettu ennen kuin käsiteltävä osio
  voidaan vahvistaa."
  {:johto-ja-hallintokorvaus {:kumoa-osiot #{:tavoite-ja-kattohinta}}
   :erillishankinnat {:kumoa-osiot #{:tavoite-ja-kattohinta}}
   :hoidonjohtopalkkio {:kumoa-osiot #{:tavoite-ja-kattohinta}}
   :hankintakustannukset {:kumoa-osiot #{:tavoite-ja-kattohinta}}

   :tavoite-ja-kattohinta {:vahvistus-vaadittu-osiot
                           #{:johto-ja-hallintokorvaus :erillishankinnat :hoidonjohtopalkkio
                             :hankintakustannukset}}

   ;; Ei riippuvuuksia tavoite- ja kattohintaan tai muihin osioihin
   :tilaajan-rahavaraukset {}})

(defn validi-suunnitelman-osio? [osio-kw]
  (boolean (suuunnitelman-osiot osio-kw)))

#?(:clj
   (defn osio-kw->osio-str [osio-kw]
     (if (validi-suunnitelman-osio? osio-kw)
       (name osio-kw)
       (throw+ {:type "Error"
                :virheet {:koodi "ERROR"
                          :viesti (str "Osion tunniste ei ole validi. Tunniste: " osio-kw)}}))))

(defn tallennettava-asia->suunnitelman-osio
  "Palauttaa kustannussuunnitelman osion tunnisteen, johon annettu tallennettava asia liittyy."
  [asia]
  (case asia
    (:toimistokulut :johto-ja-hallintokorvaus) :johto-ja-hallintokorvaus
    :erillishankinnat :erillishankinnat
    :hoidonjohtopalkkio :hoidonjohtopalkkio
    (:hankintakustannus :laskutukseen-perustuva-hankinta
      :akilliset-hoitotyot :kolmansien-osapuolten-aiheuttamat-vahingot
      :rahavaraus-lupaukseen-1) :hankintakustannukset
    :tilaajan-varaukset :tilaajan-rahavaraukset))
