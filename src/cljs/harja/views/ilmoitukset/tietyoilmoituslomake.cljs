(ns harja.views.ilmoitukset.tietyoilmoituslomake
  (:require [reagent.core :as r]
            [harja.ui.lomake :as lomake]
            [harja.pvm :as pvm]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.tiedot.ilmoitukset.tietyoilmoitukset :as tiedot]
            [harja.domain.tietyoilmoitukset :as t]
            [reagent.core :refer [atom] :as r]
            [harja.ui.grid :refer [muokkaus-grid]]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.valinnat :refer [urakan-hoitokausi-ja-aikavali]]
            [harja.loki :refer [tarkkaile! log]]
            [cljs.pprint :refer [pprint]]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.yleiset :as yleiset :refer [ajax-loader linkki livi-pudotusvalikko +korostuksen-kesto+
                                                  kuvaus-ja-avainarvopareja]]
            [harja.fmt :as fmt]
            [clojure.string :as str]
            [harja.ui.kentat :as kentat]))

(def koskee-valinnat [[nil "Ilmoitus koskee..."]
                      [:ensimmainen "Ensimmäinen ilmoitus työstä"],
                      [:muutos "Korjaus/muutos aiempaan tietoon"],
                      [:tyovaihe "Työvaihetta koskeva ilmoitus"],
                      [:paattyminen "Työn päättymisilmoitus"]]) ;; pidetäänkö päättymisilmoitus, oliko puhetta että jätetään pois?

(defn- urakka-valinnat [urakat]
  (into [[nil "Ei liity HARJA-urakkaan"]]
        (map (juxt (comp str :id) :nimi))
        urakat))


(defn- pvm-vali-paivina [p1 p2]
  (when (and p1 p2)
    (.toFixed (/ (Math/abs (- p1 p2)) (* 1000 60 60 24)) 2)))

(def tyhja-kentta {:nimi :blank
                   :otsikko "" :hae (constantly "")
                   :muokattava false
                   :tyyppi :tyhja})

(defn dp [val msg]
  (log msg (with-out-str (cljs.pprint/pprint val)))
  val)

(defn- muunna-nopeusrajoitukset-muokkaus-gridille [nr-tiedot]
  ;; muuttaa ::t/nopeusrajoitukset -avaimen tiedont, esim:
  ;; [{:harja.domain.tietyoilmoitukset/rajoitus "30", :harja.domain.tietyoilmoitukset/matka 100}])
  ;; mapiksi {indeksi {rajoitus matka}} -tupleja, esim:
  ;; {0 {:rajoitus 100, :matka 200}, 1 {:rajoitus 300, :matka 20}}
  (apply merge
         (map-indexed
          (fn [indeksi rajoitus-map]
            {indeksi {:rajoitus (::t/rajoitus rajoitus-map) :matka (::t/matka rajoitus-map)}})
          nr-tiedot)))

(defn nopeusrajoitukset-komponentti-grid [e! nr-tiedot]
  (log "gridin dataksi r/wrapatty" (pr-str (muunna-nopeusrajoitukset-muokkaus-gridille nr-tiedot)))
  (log "nr-tiedot oli" (pr-str nr-tiedot))
  (if (some? nr-tiedot)
    [muokkaus-grid {:otsikko ""
                    :voi-muokata? (constantly true)
                    :voi-poistaa? (constantly true)
                    :piilota-toiminnot? false
                    :tyhja "Ei nopeusrajoituksia"
                    :jarjesta :jarjestysnro
                    :tunniste :jarjestysnro}
     [{:otsikko "Rajoitus (km/h)" :nimi :rajoitus :tyyppi :positiivinen-numero}
      {:otsikko "Matka (m)" :nimi :matka :tyyppi :positiivinen-numero }]
     (r/wrap (muunna-nopeusrajoitukset-muokkaus-gridille nr-tiedot)
             #(e! (tiedot/->PaivitaNopeusrajoituksetGrid %)))]
    ;; else
    (log "nr-komponentti sai nil")))

(def paiva-lyhyt #(str/upper-case (subs % 0 2)))

(defn tyoajat-komponentti-grid [e! tyoajat]
  (log "TYÖAJAT: " (pr-str tyoajat))
  [muokkaus-grid {:otsikko ""
                  :voi-muokata? (constantly true)
                  :voi-poistaa? (constantly true)
                  :piilota-toiminnot? false
                  :tyhja "Ei työaikoja"}
   [{:otsikko "Viikonpäivät" :nimi ::t/paivat :tyyppi :checkbox-group
     :tasaa :keskita
     :vaihtoehdot ["maanantai" "tiistai" "keskiviikko" "torstai" "perjantai" "lauantai" "sunnuntai"]
     :vaihtoehto-nayta paiva-lyhyt
     :nayta-rivina? true
     :leveys 5}
    {:otsikko "Alkuaika" :tyyppi :aika :placeholder "esim. 08:00" :nimi ::t/alkuaika
     :leveys 1}
    {:otsikko "Loppuaika" :tyyppi :aika :placeholder "esim. 18:00" :nimi ::t/loppuaika
     :leveys 1}
    ]
   (r/wrap (into {}
                 (map-indexed (fn [i ta]
                                [i (update ta ::t/paivat
                                           #(into #{} %))]))
                 tyoajat)
           #(e! (tiedot/->PaivitaTyoajatGrid (vals %))))])

(defn lomake [e! tallennus-kaynnissa? ilmoitus kayttajan-urakat]
  [:div
   [:span
    [napit/takaisin "Palaa ilmoitusluetteloon" #(e! (tiedot/->PoistaIlmoitusValinta))]
    [lomake/lomake {:otsikko "Muokkaa ilmoitusta"
                    :muokkaa! #(do

                                 #_(log "muokkaa" (pr-str %))
                                 (e! (tiedot/->IlmoitustaMuokattu %)))}
     [(lomake/ryhma
       "Ilmoitus koskee"
       {:nimi :koskee
        :otsikko ""
        :tyyppi :valinta
        :valinnat koskee-valinnat
        :valinta-nayta second
        :valinta-arvo first
        :muokattava? (constantly true)}
       )
      (lomake/ryhma "Tiedot koko kohteesta"
                    {:nimi :urakan-nimi-valinta
                     :otsikko "Liittyy urakkaan"
                     :tyyppi :valinta
                     :valinnat (urakka-valinnat kayttajan-urakat)
                     :valinta-nayta second :valinta-arvo first
                     :muokattava? (constantly true)}
                    (if (:urakan-nimi-valinta ilmoitus)
                      (assoc tyhja-kentta :nimi :blank-1)
                      ;; else
                      {:nimi :urakan-nimi-syotetty
                       :otsikko "Projektin tai urakan nimi"
                       :tyyppi :string
                       :muokattava? (constantly true)})
                    (if (:urakan-nimi-valinta ilmoitus)
                      (assoc tyhja-kentta :nimi :blank-2)
                      ;; else
                      {:otsikko "Kohde urakassa"
                       :nimi :kohde
                       :tyyppi :string})

                    {:nimi ::t/urakoitsijan-nimi
                     :otsikko "Urakoitsijan nimi"
                     :muokattava? (constantly true)
                     :tyyppi :string}
                    {:nimi :urakoitisijan-yhteyshenkilo-nimi
                     :otsikko "Urakoitsijan yhteyshenkilö"
                     ;; tuleeko yhdistämisessä muoakttaessa haankluuksia kun pitää taas erottaa?
                     ;; -> ehkä täytyy poiketa rautalangasta tässä
                     :hae #(-> %  ::t/urakoitsijayhteyshenkilo tiedot/henkilo->nimi)
                     :muokattava? (constantly true)
                     :tyyppi :string}
                    {:nimi :urakoitisijanyhteyshenkilo-matkapuhelin
                     :otsikko "Puhelinnumero"
                     :hae #(-> % ::t/urakoitsijayhteyshenkilo ::t/matkapuhelin)
                     :tyyppi :puhelin}
                    {:nimi ::t/tilaajan-nimi
                     :otsikko "Tilaajan nimi"
                     :muokattava? (constantly true)
                     :tyyppi :string}
                    {:nimi :tilaajan-yhteyshenkilo-nimi
                     :otsikko "Tilaajan yhteyshenkilö"
                     :hae #(-> %  ::t/tilaajayhteyshenkilo tiedot/henkilo->nimi)

                     :muokattava? (constantly true)
                     :tyyppi :string}
                    {:nimi :tilaajan-yhteyshenkilo-matkapuhelin
                     :otsikko "Puhelinnumero"
                     :hae #(-> % ::t/tilaajayhteyshenkilo ::t/matkapuhelin)
                     :tyyppi :puhelin}
                    #_{:nimi :tienumero
                       :otsikko "Tienumero"
                       :tyyppi :positiivinen-numero
                       :muokattava? (constantly true)}
                    {:otsikko "Osoite"
                     :nimi ::t/osoite
                     :pakollinen? true
                     :tyyppi :tierekisteriosoite
                     :avaimet kentat/tr-osoite-domain-avaimet
                     :ala-nayta-virhetta-komponentissa? true
                     :validoi [[:validi-tr "Reittiä ei saada tehtyä" [::t/osoite]]]
                     :sijainti (r/wrap (:sijainti ilmoitus) #(e! (tiedot/->PaivitaSijainti %)))
                     }
                    {:otsikko "Tien nimi" :nimi ::t/tien-nimi
                     :tyyppi :string}
                    {:otsikko "Kunta/kunnat" :nimi ::t/kunnat
                     :tyyppi :string}
                    {:otsikko "Työn alkupiste (osoite, paikannimi)" :nimi ::t/alkusijainnin-kuvaus
                     :tyyppi :string}
                    {:otsikko "Työn aloituspvm" :nimi ::t/alku :tyyppi :pvm}
                    {:otsikko "Työn loppupiste (osoite, paikannimi)" :nimi ::t/loppusijainnin-kuvaus
                     :tyyppi :string}
                    {:otsikko "Työn lopetuspvm" :nimi ::t/loppu :tyyppi :pvm}
                    {:otsikko "Työn pituus" :nimi :tyon-pituus
                     :tyyppi :positiivinen-numero
                     :placeholder "(Tyon pituus metreinä)"
                     })

      #_(lomake/ryhma "Työvaihe"

                      {:otsikko "Työn alkupiste (osoite, paikannimi)" :nimi :alkusijainnin_kuvaus_b
                       :tyyppi :string}
                      {:otsikko "Työn aloituspvm" :nimi :alku_b :tyyppi :pvm}
                      {:otsikko "Työn loppupiste (osoite, paikannimi)" :nimi :loppusijainnin_kuvaus_b
                       :tyyppi :string}
                      {:otsikko "Työn lopetuspvm" :nimi :loppu_b :tyyppi :pvm}
                      {:otsikko "Työn pituus" :nimi :tyon-pituus_b
                       :tyyppi :positiivinen-numero
                       :hae #(pvm-vali-paivina (:alku %) (:loppu %))}
                      )
      (lomake/ryhma "Työn tyyppi"
                    {:otsikko "Tienrakennustyöt"
                     :nimi :tyotyypit-a
                     :tyyppi :checkbox-group
                     :vaihtoehdot (map first tiedot/tyotyyppi-vaihtoehdot-tienrakennus)
                     :vaihtoehto-nayta tiedot/tyotyyppi-vaihtoehdot-map
                     :disabloi? (constantly false)}
                    {:otsikko "Huolto- ja ylläpitotyöt"
                     :nimi :tyotyypit-b
                     :tyyppi :checkbox-group
                     :vaihtoehdot (map first tiedot/tyotyyppi-vaihtoehdot-huolto)
                     :vaihtoehto-nayta tiedot/tyotyyppi-vaihtoehdot-map
                     :disabloi? (constantly false)}
                    {:otsikko "Asennustyöt"
                     :nimi :tyotyypit-c
                     :tyyppi :checkbox-group
                     :vaihtoehdot (map first tiedot/tyotyyppi-vaihtoehdot-asennus)
                     :vaihtoehto-nayta tiedot/tyotyyppi-vaihtoehdot-map
                     :disabloi? (constantly false)}
                    {:otsikko "Muut"
                     :nimi :tyotyypit-d
                     :tyyppi :checkbox-group-muu
                     :vaihtoehdot (map first tiedot/tyotyyppi-vaihtoehdot-muut)
                     :vaihtoehto-nayta tiedot/tyotyyppi-vaihtoehdot-map ;; -> muu
                     :muu-vaihtoehto "Muu, mikä?"
                     :muu-kentta {:otsikko "" :nimi :jotain :tyyppi :string :placeholder "(Muu tyyppi?)"}
                     :disabloi? (constantly false)}
                    )
      {:otsikko "Päivittäinen työaika"
       :nimi ::t/tyoajat
       :tyyppi :komponentti
       :komponentti #(->> % :data ::t/tyoajat (tyoajat-komponentti-grid e!))
       :palstoja 2}
      (lomake/ryhma "Vaikutukset liikenteelle"
                    {:otsikko "Arvioitu viivytys normaalissa liikenteessä (min)"
                     :nimi ::t/viivastys-normaali-liikenteessa
                     :tyyppi :positiivinen-numero}
                    {:otsikko "Arvioitu viivytys ruuhka-aikana (min)"
                     :nimi ::t/viivastys-ruuhka-aikana
                     :tyyppi :positiivinen-numero
                     }
                    {:otsikko "Kaistajärjestelyt"
                     :tyyppi :checkbox-group-muu
                     :nimi :tietyon_kaistajarjestelyt
                     :vaihtoehdot (map first tiedot/kaistajarjestelyt-vaihtoehdot-map)
                     :vaihtoehto-nayta tiedot/kaistajarjestelyt-vaihtoehdot-map
                     :muu-vaihtoehto "Muu"
                     :muu-kentta {:otsikko "" :nimi :jotain :tyyppi :string :placeholder "(muu kaistajärjestely?)"}}
                    {:otsikko "Nopeusrajoitukset"
                     :tyyppi :komponentti
                     :komponentti #(->> % :data ::t/nopeusrajoitukset (nopeusrajoitukset-komponentti-grid e!))
                     :nimi :nopeusrajoitukset
                     }
                    {:otsikko "Kokorajoituksia"
                     :tyyppi :checkbox-group
                     :nimi :kokorajoituksia
                     :vaihtoehdot ["Ulottumarajoituksia" ;; ->max leveys, korkeus
                                   "Painorajoitus"] ;; -> max paino

                     }
                    {:otsikko "Tien pinta työmaalla"
                     ;; tehdään muokkaus-grid tähän?
                     :tyyppi :checkbox-group
                     :nimi :tienpinta-materiaali
                     :vaihtoehdot ["Päällystetty", "Jyrsitty", "Murske"]

                     }
                    {:otsikko "Metriä:"
                     :tyyppi :positiivinen-numero
                     :nimi :tienpinta-matka}
                    {:otsikko "Kiertotie"
                     :tyyppi :checkbox-group
                     :nimi :kiertotien-pinnat-ja-mutkaisuus ;; scheman mutkaisuus + pinnat combo

                     :valinnat ["Loivat mutkat", "Jyrkät mutkat (erkanee yli 45° kulmassa)" "Päällystetty" "Murske" "Kantavuus rajoittaa", "___ tonnia"]
                     ;; ___ metriä
                     ;; __ tonnia

                     }
                    {:otsikko "Kulkurajoituksia"
                     :tyyppi :checkbox-group-muu
                     :nimi :liikenteenohjaus
                     :vaihtoehdot ["Liikennevalot" "Liikenteen ohjaaja" "Satunnaisia (aikataulu, jos yli 5 min)" "Aikataulu:"]
                     :vaihtoehto-nayta identity
                     :muu-vaihtoehto "Aikataulu:"
                     :muu-kentta {:otsikko "" :nimi :jotain :tyyppi :string :placeholder "(muu kaistajärjestely?)"}
                     }
                    )
      (lomake/ryhma "Vaikutussuunta"
                    {:otsikko ""
                     :tyyppi :checkbox-group
                     :nimi ::t/vaikutussuunta
                     :vaihtoehdot (map first tiedot/vaikutussuunta-vaihtoehdot-map) ;; -> kaupunki?
                     :vaihtoehto-nayta tiedot/vaikutussuunta-vaihtoehdot-map
                     ;; muu?
                     }

                    )
      (lomake/ryhma "Lisätietoja"
                    {:otsikko ""
                     :nimi ::t/lisatietoja
                     :tyyppi :string
                     })
      (lomake/ryhma "Ilmoittaja"
                    {:nimi :ilmoittaja-nimi
                     :otsikko "Nimi"
                     :hae #(-> %  ::t/urakoitsijayhteyshenkilo tiedot/henkilo->nimi)
                     :muokattava? (constantly true)
                     :tyyppi :string}
                    {:nimi :ilmoittaja-matkapuhelin
                     :otsikko "Puhelinnumero"
                     :hae #(-> % ::t/ilmoittaja ::t/matkapuhelin)
                     :tyyppi :puhelin}
                    {:otsikko "Päivämäärä"
                     :nimi :luotu
                     :tyyppi :string}
                    )
      ]
     ilmoitus]]
   [napit/tallenna
    "Tallenna ilmoitus"
    #(e! (tiedot/->TallennaIlmoitus (lomake/ilman-lomaketietoja ilmoitus)))
    {:disabled tallennus-kaynnissa?
     :tallennus-kaynnissa? tallennus-kaynnissa?
     :ikoni (ikonit/tallenna)}]])
