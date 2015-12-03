(ns harja.palvelin.raportointi.raportit.ilmoitus
  "Ilmoitusraportti"
  (:require [taoensso.timbre :as log]
            [harja.palvelin.raportointi.raportit.yleinen :refer [raportin-otsikko vuosi-ja-kk vuosi-ja-kk-fmt kuukaudet
                                                                 pylvaat ei-osumia-aikavalilla-teksti]]
            [harja.domain.roolit :as roolit]
            [clj-time.coerce :as tc]
            [harja.domain.ilmoitusapurit :refer [+ilmoitustyypit+ ilmoitustyypin-nimi ilmoitustyypin-lyhenne +ilmoitustilat+]]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.hallintayksikot :as hallintayksikot-q]
            [harja.palvelin.palvelut.ilmoitukset :as ilmoituspalvelu]
            [clj-time.core :as t]
            [clj-time.local :as l]
            [harja.pvm :as pvm]))


(defn muodosta-ilmoitusraportti-urakalle [db user {:keys [urakka-id alkupvm loppupvm]}]
  (log/debug "Haetaan urakan ilmoitukset raporttia varten. Urakka-id: " urakka-id
             " alkupvm: " alkupvm " loppupvm: " loppupvm)
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [;; [db user hallintayksikko urakka tilat tyypit aikavali hakuehto]
        ;; haetaan urakan konttekstissa aina kuukauden tiedot. Sopii yhteen työmaakokouskäytännön kanssa.
        ilmoitukset (ilmoituspalvelu/hae-ilmoitukset
                      db user nil urakka-id +ilmoitustilat+ +ilmoitustyypit+
                      [alkupvm loppupvm] "")
        _ (log/debug "ilmoitukset ilmoitusrapsaa varten: " ilmoitukset)]
    ilmoitukset))

(defn muodosta-ilmoitusraportti-hallintayksikolle [db user {:keys [hallintayksikko-id alkupvm loppupvm]}]
  (log/debug "Haetaan hallintayksikon toteutuneet materiaalit raporttia varten: " hallintayksikko-id alkupvm loppupvm)
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [ilmoitukset (ilmoituspalvelu/hae-ilmoitukset
                      db user hallintayksikko-id nil +ilmoitustilat+ +ilmoitustyypit+
                      [alkupvm loppupvm] "")
        _ (log/debug "ilmoitukset ilmoitusrapsaa varten: " ilmoitukset)]
    ilmoitukset))

(defn muodosta-ilmoitusraportti-koko-maalle [db user {:keys [alkupvm loppupvm]}]
  (log/debug "Haetaan koko maan toteutuneet materiaalit raporttia varten: " alkupvm loppupvm)
  (roolit/vaadi-rooli user "tilaajan kayttaja")
  (let [ilmoitukset (ilmoituspalvelu/hae-ilmoitukset
                      db user nil nil +ilmoitustilat+ +ilmoitustyypit+
                      [alkupvm loppupvm] "")
        _ (log/debug "ilmoitukset ilmoitusrapsaa varten: " ilmoitukset)]
    ilmoitukset))



(defn suorita [db user {:keys [urakka-id hallintayksikko-id alkupvm loppupvm] :as parametrit}]
  (let [konteksti (cond urakka-id :urakka
                        hallintayksikko-id :hallintayksikko
                        :default :koko-maa)
        kyseessa-kk-vali? (pvm/kyseessa-kk-vali? alkupvm loppupvm)
        ilmoitukset (ilmoituspalvelu/hae-ilmoitukset
                      db user hallintayksikko-id urakka-id +ilmoitustilat+ +ilmoitustyypit+
                      [alkupvm loppupvm] "")

        ;; graafia varten haetaan joko ilmoitukset pitkältä aikaväliltä tai jos kk raportti, niin hoitokaudelta
        hoitokauden-alkupvm (first (pvm/paivamaaran-hoitokausi alkupvm))
        ilmoitukset-hoitokaudella (when kyseessa-kk-vali?
                                    (ilmoituspalvelu/hae-ilmoitukset
                                      db user hallintayksikko-id urakka-id +ilmoitustilat+ +ilmoitustyypit+
                                      [hoitokauden-alkupvm loppupvm] ""))
        tpp-kuukausittain (frequencies (map (comp vuosi-ja-kk :ilmoitettu)
                                            (filter #(= :toimenpidepyynto (:ilmoitustyyppi %))
                                                    (if kyseessa-kk-vali?
                                                      ilmoitukset-hoitokaudella
                                                      ilmoitukset))))
        urk-kuukausittain (frequencies (map (comp vuosi-ja-kk :ilmoitettu)
                                            (filter #(= :kysely (:ilmoitustyyppi %)) (if kyseessa-kk-vali?
                                                                                       ilmoitukset-hoitokaudella
                                                                                       ilmoitukset))))
        tur-kuukausittain (frequencies (map (comp vuosi-ja-kk :ilmoitettu)
                                            (filter #(= :tiedoitus (:ilmoitustyyppi %)) (if kyseessa-kk-vali?
                                                                                          ilmoitukset-hoitokaudella
                                                                                          ilmoitukset))))
        graafin-alkupvm (if kyseessa-kk-vali?
                          hoitokauden-alkupvm
                          alkupvm)
        hoitokaudella-tahan-asti-opt (if kyseessa-kk-vali? " hoitokaudella tähän asti " "")
        raportin-nimi "Ilmoitusraportti"
        otsikko (raportin-otsikko
                  (case konteksti
                    :urakka (:nimi (first (urakat-q/hae-urakka db urakka-id)))
                    :hallintayksikko (:nimi (first (hallintayksikot-q/hae-organisaatio db hallintayksikko-id)))
                    :koko-maa "KOKO MAA")
                  raportin-nimi alkupvm loppupvm)
        ilmoitukset-urakan-mukaan (group-by :urakka ilmoitukset)
        nayta-pylvaat? (or (and (> (count ilmoitukset) 0)
                                (not= (vuosi-ja-kk alkupvm) (vuosi-ja-kk loppupvm)))
                           (and (> (count ilmoitukset-hoitokaudella) 0)
                                kyseessa-kk-vali?))]
    [:raportti {:nimi raportin-nimi}
     [:taulukko {:otsikko                    otsikko
                 :viimeinen-rivi-yhteenveto? true}
      (into []
            (concat
              [{:otsikko "Urakka"}]
              (map (fn [ilmoitustyyppi]
                     {:otsikko (str (ilmoitustyypin-lyhenne ilmoitustyyppi)
                                    " ("
                                    (ilmoitustyypin-nimi ilmoitustyyppi)
                                    ")")})
                   [:toimenpidepyynto :kysely :tiedoitus])))
      (into
        []
        (concat
          ;; Tehdään rivi jokaiselle urakalle, ja näytetään niiden erityyppistem ilmoitusten määrä
          (for [[urakka ilmoitukset] ilmoitukset-urakan-mukaan]
            (let [urakan-nimi (or (:nimi (first (urakat-q/hae-urakka db urakka))) "Ei urakkaa")
                  tpp (count (filter #(= :toimenpidepyynto (:ilmoitustyyppi %)) ilmoitukset))
                  urk (count (filter #(= :kysely (:ilmoitustyyppi %)) ilmoitukset))
                  tur (count (filter #(= :tiedoitus (:ilmoitustyyppi %)) ilmoitukset))]
              [urakan-nimi tpp urk tur]))

          ;; Tehdään yhteensä rivi, jossa kaikki ilmoitukset lasketaan yhteen materiaalin perusteella
          (let [tpp-yht (count (filter #(= :toimenpidepyynto (:ilmoitustyyppi %)) ilmoitukset))
                urk-yht (count (filter #(= :kysely (:ilmoitustyyppi %)) ilmoitukset))
                tur-yht (count (filter #(= :tiedoitus (:ilmoitustyyppi %)) ilmoitukset))]
            [(concat ["Yhteensä"]
                     [tpp-yht urk-yht tur-yht])])))]

     (when nayta-pylvaat?
       (if-not (empty? tpp-kuukausittain)
         (pylvaat (str "TPP kuukausittain" hoitokaudella-tahan-asti-opt) graafin-alkupvm loppupvm tpp-kuukausittain)
         (ei-osumia-aikavalilla-teksti "TPP-ilmoituksia" graafin-alkupvm loppupvm)))
     (when nayta-pylvaat?
       (if-not (empty? urk-kuukausittain)
         (pylvaat (str "URK kuukausittain" hoitokaudella-tahan-asti-opt) graafin-alkupvm loppupvm urk-kuukausittain)
         (ei-osumia-aikavalilla-teksti "URK-ilmoituksia" graafin-alkupvm loppupvm)))
     (when nayta-pylvaat?
       (if-not (empty? tur-kuukausittain)
         (pylvaat (str "TUR kuukausittain" hoitokaudella-tahan-asti-opt) graafin-alkupvm loppupvm tur-kuukausittain)
         (ei-osumia-aikavalilla-teksti "TUR-ilmoituksia" graafin-alkupvm loppupvm)))]))

    
