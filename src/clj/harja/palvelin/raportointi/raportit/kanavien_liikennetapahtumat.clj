(ns harja.palvelin.raportointi.raportit.kanavien-liikennetapahtumat
  (:require [harja.pvm :as pvm]
            [clojure.string :as str]
            [clj-time.core :as time]
            [clj-time.coerce :as coerce]
            [harja.kyselyt.kanavat.liikennetapahtumat :as q]
            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.kohde :as k]
            [harja.domain.kanavat.lt-alus :as a]
            [harja.domain.kanavat.lt-toiminto :as t]
            [harja.domain.kayttaja :as kayttaja]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko rivi]]))

(def liikennetapahtuma-raportin-nimi "Liikennetapahtumat")

(defn- taulukko [{:keys [gridin-otsikko rivin-tiedot rivit oikealle-tasattavat]}]
  [:taulukko {:otsikko gridin-otsikko
              :oikealle-tasattavat-kentat (or oikealle-tasattavat #{})
              :tyhja "Ei Tietoja."
              :piilota-border? false
              :viimeinen-rivi-yhteenveto? false}
   rivin-tiedot rivit])

(defn- osion-otsikko [otsikko]
  [:otsikko-heading otsikko {:padding-top "50px"}])

(defn- liikennetapahtuma-rivi [klo kohde tyyppi avaus
                               palvelumuoto suunta alus aluslaji
                               aluksia matkustajia nippuja ylavesi
                               alavesi lisatiedot kuittaaja]
  (rivi
    [:varillinen-teksti {:arvo klo}]
    [:varillinen-teksti {:arvo kohde}]
    [:varillinen-teksti {:arvo tyyppi}]
    [:varillinen-teksti {:arvo avaus}]
    [:varillinen-teksti {:arvo palvelumuoto}]
    [:varillinen-teksti {:arvo suunta}]
    [:varillinen-teksti {:arvo alus}]
    [:varillinen-teksti {:arvo aluslaji}]
    [:varillinen-teksti {:arvo aluksia}]
    [:varillinen-teksti {:arvo matkustajia}]
    [:varillinen-teksti {:arvo nippuja}]
    [:varillinen-teksti {:arvo ylavesi}]
    [:varillinen-teksti {:arvo alavesi}]
    [:varillinen-teksti {:arvo lisatiedot}]
    [:varillinen-teksti {:arvo kuittaaja}]))

(defn- koosta-liikennetapahtuma-taulukko [data]
  (let [tiedot {:rivin-tiedot (rivi
                                {:otsikko "Aika" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "vaalen-tumma-tausta" :leveys 0.75 :tyyppi :varillinen-teksti}
                                {:otsikko "Kohde" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.8 :tyyppi :varillinen-teksti}
                                {:otsikko "Tyyppi" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                                {:otsikko "Sillan avaus" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                                {:otsikko "Palvelumuoto" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 1 :tyyppi :varillinen-teksti}
                                {:otsikko "Suunta" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                                {:otsikko "Alus" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 1 :tyyppi :varillinen-teksti}
                                {:otsikko "Aluslaji" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                                {:otsikko "Aluksia" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                                {:otsikko "Matkustajia" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.7 :tyyppi :varillinen-teksti}
                                {:otsikko "Nippuja" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.5 :tyyppi :varillinen-teksti}
                                {:otsikko "Ylävesi" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                                {:otsikko "Alavesi" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 0.6 :tyyppi :varillinen-teksti}
                                {:otsikko "Lisätiedot" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 1 :tyyppi :varillinen-teksti}
                                {:otsikko "Kuittaaja" :otsikkorivi-luokka "nakyma-otsikko" :sarakkeen-luokka "nakyma-valkoinen-solu" :leveys 1 :tyyppi :varillinen-teksti})
                :rivit (mapv
                         #(liikennetapahtuma-rivi
                            (pvm/pvm-aika-klo (:aika %))
                            (:kohde %)
                            (:tyyppi %)
                            (:avaus %)
                            (:palvelumuoto %)
                            (:suunta %)
                            (:alus %)
                            (:aluslaji %)
                            (:aluksia %)
                            (:matkustajia %)
                            (:nippuja %)
                            (:ylavesi %)
                            (:alavesi %)
                            (:lisatiedot %)
                            (:kuittaaja %))
                         data)}]
    (into ()
      [(taulukko tiedot)
       (osion-otsikko liikennetapahtuma-raportin-nimi)])))

(defn suorita [db user {:keys [urakoiden-nimet valitut-urakat parametrit hakuparametrit yhteenveto]}]
  (let [{:keys [alkupvm loppupvm]} parametrit
        ;; Urakoiden-nimet on nil kun 1 urakka valittuna, haetaan tällöin valitun urakan nimi toisesta muuttujasta
        urakoiden-nimet (or urakoiden-nimet (first valitut-urakat))
        raportin-otsikko (raportin-otsikko urakoiden-nimet liikennetapahtuma-raportin-nimi alkupvm loppupvm)
        liikennetapahtumat (q/hae-liikennetapahtumat db user (assoc hakuparametrit :rajoita lt/+rajoita-tapahtumien-maara+))
        ;; Muunnetaan alusten suunta tekstiksi
        suunta->str (fn [suunta] (@lt/suunnat-atom suunta))
        ;; Käyttäjämuunnos
        kayttaja->str (fn [k]
                        (str (::kayttaja/etunimi k) " " (::kayttaja/sukunimi k)))
        ;; Tapahtuman palvelumuoto
        palvelumuoto->str (fn [tapahtuma]
                            (str/join ", "
                              (into #{} (sort (map lt/fmt-palvelumuoto
                                                (filter ::t/palvelumuoto
                                                  (::lt/toiminnot tapahtuma)))))))
        ;; Avattiinko silta?
        silta-avattu->str (fn [tapahtuma]
                            (when (boolean (some (comp (partial = :avaus) ::t/toimenpide) (::lt/toiminnot tapahtuma)))
                              "✓"))
        ;; Tapahtuman toimenpiteiden mäppäys
        toimenpide->str (fn [tapahtuma]
                          (str/join ", "
                            (into #{} (sort (keep (comp lt/kaikki-toimenpiteet->str
                                                    ::t/toimenpide)
                                              (::lt/toiminnot tapahtuma))))))
        ;; Järjestetään tapahtumat 
        jarjesta-tapahtumat (fn [tapahtumat]
                              (sort-by
                                (juxt :aika :nimi :alus :aluslaji :aluksia)
                                (fn [[a-aika & _ :as a] [b-aika & _ :as b]]
                                  ;; Konverttaa java.sql.Timestamp -> org.joda.time.DateTime, muuten tulee erroria
                                  (when (and a b)
                                    (let [a-joda-time (coerce/from-sql-time a-aika)
                                          b-joda-time (coerce/from-sql-time b-aika)]
                                      (if (time/equal? a-joda-time b-joda-time)
                                        (compare a b)
                                        (time/after? a-joda-time b-joda-time)))))
                                tapahtumat))
        ;; Mäpätään tapahtumarivit raportille
        tapahtumarivit (mapcat
                         (fn [tapahtuma]
                           (let [yleiset {:aika (::lt/aika tapahtuma)
                                          :kohde (-> tapahtuma ::lt/kohde ::k/nimi)
                                          :tyyppi (toimenpide->str tapahtuma)
                                          :avaus (silta-avattu->str tapahtuma)
                                          :palvelumuoto (palvelumuoto->str tapahtuma)
                                          :ylavesi (-> tapahtuma ::lt/vesipinta-ylaraja)
                                          :alavesi (-> tapahtuma ::lt/vesipinta-alaraja)
                                          :lisatiedot (-> tapahtuma ::lt/lisatieto)
                                          :kuittaaja (-> tapahtuma ::lt/kuittaaja kayttaja->str)}
                                 alukset (get tapahtuma ::lt/alukset)]
                             ;; Niputetaan alukset mukaan
                             (if (seq alukset)
                               (map (fn [alus]
                                      (merge yleiset
                                        {:suunta (-> alus ::a/suunta suunta->str)
                                         :alus (-> alus ::a/nimi)
                                         :aluslaji (-> alus ::a/laji a/aluslaji->laji-str)
                                         :aluksia (-> alus ::a/lkm)
                                         :matkustajia (-> alus ::a/matkustajalkm)
                                         :nippuja (-> alus ::a/nippulkm)}))
                                 alukset)
                               [yleiset])))
                         liikennetapahtumat)

        tapahtumarivit (jarjesta-tapahtumat tapahtumarivit)
        tapahtumarivit (take lt/+rajoita-tapahtumien-maara+ tapahtumarivit)
        tapahtumien-maara (count tapahtumarivit)
        _ (println "\n Tapahtumien määrä: " tapahtumien-maara  (> tapahtumien-maara lt/+rajoita-tapahtumien-maara+) lt/+rajoita-tapahtumien-maara+" \n --------")
        ]

    [:raportti {:orientaatio :landscape
                :nimi raportin-otsikko}
     (koosta-liikennetapahtuma-taulukko tapahtumarivit)
     [:liikenneyhteenveto yhteenveto]]))
