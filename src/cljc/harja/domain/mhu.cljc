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

;; Tehtäviin kohdistetut rahavaraukset
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

(def tunneleiden-hoito-liikenneympariston-hoito-tunniste "4342cd30-a9b7-4194-94ee-00c0ce1f6fc6")

(def rahavaraus-lupaukseen-1-mhu-yllapito-tunniste "794c7fbf-86b0-4f3e-9371-fb350257eb30")
;; HOX: Tämä on tunniste "Muut tavoitehintaan vaikuttavat rahavaraukset"-tehtävälle.
(def muut-rahavaraukset-mhu-yllapito-tunniste "548033b7-151d-4202-a2d8-451fba284d92")

;; Suoraan tehtäväryhmiin kohdistetut rahavaraukset
(def erillishankinnat-tunniste "37d3752c-9951-47ad-a463-c1704cf22f4c")
(def johto-ja-hallintokorvaukset-tunniste "a6614475-1950-4a61-82c6-fda0fd19bb54")


(defn toimenpide->toimenpide-avain [v]
  (key-from-val toimenpide-avain->toimenpide v))

(def toimenpiteen-rahavarausten-tyypit
  "Toimenpiteisiin liittyvien rahavarausten kaikki mahdolliset tyypit."
  #{:akilliset-hoitotyot :kolmansien-osapuolten-aiheuttamat-vahingot
    ;; Muut rahavaraukset
    :tunneleiden-hoidot :rahavaraus-lupaukseen-1 :muut-rahavaraukset})

(def rahavarauksen-tyyppi->rivin-otsikko
  "Rahavaraukset gridin rivin otsikko."
  {:akilliset-hoitotyot "Äkillinen hoitotyö"
   :kolmansien-osapuolten-aiheuttamat-vahingot "Vahinkojen korjaukset"
   :tunneleiden-hoidot "Tunneleiden hoito"
   :rahavaraus-lupaukseen-1 "Tilaajan rahavaraus lupaukseen 1"
   :muut-rahavaraukset "Muut tavoitehintaan vaikuttavat rahavaraukset"})

(def tallennettava-asia->toteumatyyppi
  "Nämä tallennettavat asiat liittyvät ainoastaan kustannusarvioidut_tyot tauluun.
  Mappaa tallennettavan asian oikeaksi toteumatyypiksi."
  {:hoidonjohtopalkkio "laskutettava-tyo"
   :toimistokulut "laskutettava-tyo"
   :erillishankinnat "laskutettava-tyo"

   ;; -- Toimenpiteen rahavaraukset --
   :kolmansien-osapuolten-aiheuttamat-vahingot "vahinkojen-korjaukset"
   :akilliset-hoitotyot "akillinen-hoitotyo"
   :tunneleiden-hoidot "muut-rahavaraukset"
   :rahavaraus-lupaukseen-1 "muut-rahavaraukset"
   ;; HOX: Älä sekoita rahavarauksen tyyppiä toteumatyyppiin, vaikka sillä sattuukin olemaan sama nimi.
   :muut-rahavaraukset "muut-rahavaraukset"

   ;; -- Toimenpiteen maaramitattavat tyot--
   :toimenpiteen-maaramitattavat-tyot "laskutettava-tyo"

   ;; -- Tilaajan varaukset --
   :tilaajan-varaukset "laskutettava-tyo"})

(defn toteumatyyppi->tallennettava-asia [v]
  (key-from-val tallennettava-asia->toteumatyyppi v))

(defn kustannusarvioitu-tyo-laske-indeksikorjaus?
  "Tämä liittyy kustannusarvioitujen töiden tallennettaviin asioihin.
  Palauttaa booleanin, jonka perusteella päätetään lasketaanko tallennettavalle asialle automaattisesti indeksikorjaus
  vai ei."
  [tallennettava-asia]
  {:pre [(keyword? tallennettava-asia)]}
  ;; Ainoastaan "tilaajan varaukset" on tällä hetkellä sellainen "tallennettava asia", jolle ei lasketa indeksikorjausta.
  (not (= :tilaajan-varaukset tallennettava-asia)))


(def tallennettava-asia->tehtava
  "Nämä tallennettavat asiat liittyvät ainoastaan kustannusarvioidut_tyot tauluun.
  Mappaa tallennettavan asian tehtävän tunnisteeksi."
  {:hoidonjohtopalkkio hoidonjohtopalkkio-tunniste
   :toimistokulut toimistokulut-tunniste

   ;; Kolmansien osapuolten aiheuttamat vahingot = "vahinkojen korjaukset"
   :kolmansien-osapuolten-aiheuttamat-vahingot {:liikenneympariston-hoito kolmansien-osapuolten-vahingot-liikenneympariston-hoito-tunniste}
   :akilliset-hoitotyot {:liikenneympariston-hoito akilliset-hoitotyot-liikenneympariston-hoito-tunniste}
   :tunneleiden-hoidot {:liikenneympariston-hoito tunneleiden-hoito-liikenneympariston-hoito-tunniste}
   :rahavaraus-lupaukseen-1 {:mhu-yllapito rahavaraus-lupaukseen-1-mhu-yllapito-tunniste}
   :muut-rahavaraukset {:mhu-yllapito muut-rahavaraukset-mhu-yllapito-tunniste}})

(defn tehtava->tallennettava-asia [v]
  (key-from-val tallennettava-asia->tehtava v))

(def tallennettava-asia->tehtavaryhma
  "Nämä tallennettavat asiat liittyvät ainoastaan kustannusarvioidut_tyot tauluun.
  Mappaa tallennettavan asian tehtäväryhmän tunnisteeksi."
  {:erillishankinnat        erillishankinnat-tunniste
   ;; Kyseessä on johto-ja-hallintokorvaus-tehtäväryhmä.
   :tilaajan-varaukset      johto-ja-hallintokorvaukset-tunniste})

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
      :akilliset-hoitotyot :kolmansien-osapuolten-aiheuttamat-vahingot :tunneleiden-hoidot
      :rahavaraus-lupaukseen-1 :muut-rahavaraukset) :hankintakustannukset
    :tilaajan-varaukset :tilaajan-rahavaraukset))
