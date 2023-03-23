(ns harja.palvelin.raportointi.raportit.ilmoitukset
  "Ilmoitus välilehden raportti"
  (:require 
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
            [taoensso.timbre :as log]))

(defn- rivi-taulukolle
  [valiotsikko lihavoi?]
  (rivi
   [:varillinen-teksti {:arvo (str valiotsikko) :lihavoi? lihavoi?}]
   [:varillinen-teksti {:arvo 0.0M :fmt :raha :lihavoi? lihavoi?}]))

(defn- taulukko [{:keys [otsikko laskutettu-teksti laskutetaan-teksti
                         kyseessa-kk-vali? sheet-nimi]}]
  (let [rivit (into []
                    (remove nil?
                            (cond
                              :else
                              [(rivi-taulukolle "test" false)])))]

    [:taulukko {:oikealle-tasattavat-kentat #{1 2}
                :viimeinen-rivi-yhteenveto? false
                :sheet-nimi sheet-nimi}

     (rivi
      {:otsikko otsikko
       :leveys 36}
      {:otsikko laskutettu-teksti :leveys 29 :tyyppi :varillinen-teksti}
      (when kyseessa-kk-vali?
        {:otsikko laskutetaan-teksti :leveys 29 :tyyppi :varillinen-teksti}))

     rivit]))

(defn suorita [db user {:keys [alkupvm loppupvm test] :as parametrit}]
  (log/debug "Ilmoitukset raportti PARAMETRIT: " (pr-str parametrit))

  [:raportti {:nimi (str "Ilmoitus raportti test test")
              :otsikon-koko :iso}

   [:otsikko-heading-small (str "testi")]
   [:otsikko "otsikko"]

   (taulukko {:otsikko "taulukko"
              :sheet-nimi "sheetti"})

   [:otsikko-heading "Otsikko-heading"]])