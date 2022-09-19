(ns harja.tiedot.urakka.urakka
  "MHU-urakoiden tila täällä. Hyvä olisi joskus saada muutkin tänne, yhden atomin alle."
  (:require [reagent.core :refer [atom cursor]]
            [clojure.core.async :refer [chan]]
            [clojure.spec.alpha :as s]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.toteuma :as t]
            [harja.ui.validointi :as validointi]
            [harja.loki :as loki]
            [harja.pvm :as pvm]
            [clojure.string :as str]
            [reagent.core :as r]
            [harja.tiedot.urakka :as u]))

(defonce kustannussuunnitelma-default {:hankintakustannukset {:valinnat {:toimenpide                     :talvihoito
                                                                         :maksetaan                      :molemmat
                                                                         :kopioidaan-tuleville-vuosille? true
                                                                         :laskutukseen-perustuen-valinta #{}}}
                                       :suodattimet          {:hankinnat                      {:toimenpide                     :talvihoito
                                                                                               :maksetaan                      :molemmat
                                                                                               :kopioidaan-tuleville-vuosille? true
                                                                                               :laskutukseen-perustuen-valinta #{}}
                                                              :kopioidaan-tuleville-vuosille? true}})

(def suunnittelu-default-arvot {:tehtavat             {:valinnat {:samat-tuleville false
                                                                  :nayta-aluetehtavat? true
                                                                  :nayta-suunniteltavat-tehtavat? true
                                                                  :toimenpide      nil
                                                                  :valitaso        nil
                                                                  :noudetaan       0}}
                                :kustannussuunnitelma kustannussuunnitelma-default})

(defn parametreilla [v-fn & parametrit]
  (apply r/partial v-fn parametrit))

(defn silloin-kun [pred v-fn]
  (fn [arvo]
    (if (pred)
      (v-fn arvo)
      true)))

(defn ei-pakollinen [v-fn]
  (fn [arvo]
    (if-not (str/blank? arvo)
      (v-fn arvo)
      true)))

(defn ei-nil [arvo]
  (when
    (not (nil? arvo))
    arvo))

(defn ei-tyhja [arvo]
  (when
    (or
      (number? arvo)
      (pvm/pvm? arvo)
      (not (str/blank? arvo)))
    arvo))

(defn numero [arvo]
  (when
    (number? (-> arvo js/parseFloat))
    arvo))

(defn negatiivinen-numero [arvo]
  (when
    (and (number? (-> arvo js/parseFloat))
      (not (pos? arvo)))
    arvo))

(defn maksimiarvo [maksimi arvo]
  (when (< arvo maksimi)
    arvo))

(defn paivamaara [arvo]
  (when
    (pvm/pvm? arvo)
    arvo))

(defn y-tunnus [arvo]
  (when (re-matches #"\d{7}-\d" (str arvo)) arvo))

(def validoinnit {:kulut/summa                 [ei-nil numero]
                  :kulut/negatiivinen-summa    [ei-nil negatiivinen-numero]
                  :kulut/laskun-numero         [(ei-pakollinen ei-tyhja) (ei-pakollinen ei-nil)]
                  :kulut/tehtavaryhma          [ei-nil ei-tyhja]
                  :kulut/erapaiva              [ei-nil ei-tyhja paivamaara]
                  :kulut/koontilaskun-kuukausi [ei-nil ei-tyhja]
                  :kulut/y-tunnus              [(ei-pakollinen y-tunnus)]
                  :kulut/lisatyon-lisatieto    [ei-nil ei-tyhja]
                  :kulut/toimenpideinstanssi   [ei-nil ei-tyhja]})

(defn validoi!
  [{:keys [validius validi?] :as lomake-meta} lomake]
  ;(loki/log "valids" validius)
  (reduce (fn [kaikki [polku {:keys [validointi] :as validius}]]
            (as-> kaikki kaikki
                  (update kaikki :validius
                          (fn [vs]
                            (update vs polku
                                    (fn [kentta]
                                      #_(.log js/console "validoi kenttä " (pr-str kentta) ", polku " (pr-str polku) ", validointi: " (pr-str validointi)
                                              "validi?" (pr-str (validointi (get-in lomake polku)))
                                              "get-in lomake polku" (pr-str (get-in lomake polku))
                                              "lomake: " (pr-str lomake))
                                      (assoc kentta
                                        :tarkistettu? true
                                        :validointi validointi
                                        :validi? (validointi
                                                   (get-in lomake polku)))))))
                  (update kaikki :validi?
                          (fn [v?]
                            (every? (fn [[_ {validi? :validi?}]]
                                      (true? validi?))
                                    (:validius kaikki))))))
          lomake-meta
          validius))

(defn validoi-fn
  "Kutsuu vain lomakkeen kaikki validointifunktiot ja päivittää koko lomakkeen validiuden"
  ([skeema lomake]
   (validoi! skeema lomake))
  ([lomake]
   (if (nil? (meta lomake))
     lomake
     (vary-meta
       lomake
       validoi!
       lomake))))

(defn- koske! 
  [validius polku]
  (update validius polku assoc :koskettu? true))

(defonce urakan-vaihto-triggerit (cljs.core/atom []))

(defn lisaa-urakan-vaihto-trigger!
  "Tämä funktio avulla voi lisätä funktion listaan, jonka kaikki funktiot ajetaan kun urakka vaihdetaan.
   Tässä voi siis tehdä jonkin tapaista siivousta."
  [f!]
  (swap! urakan-vaihto-triggerit conj f!))

(defn luo-validointi-fn
  "Yhdistää monta validointifunktiota yhdeksi"
  [validointi-fns]
  (fn [arvo]
    (let [validointi-fn (apply comp validointi-fns)]
      (not
        (nil?
          (validointi-fn arvo))))))

(defn luo-validius-tarkistukset
  "Ajatus, että lomake tietää itse, miten se validoidaan"
  [& kentat-ja-validaatiot]
  (assoc {} :validius
            (reduce (fn [k [polku validointi-fns]]
                      (assoc k polku {:validointi   (luo-validointi-fn validointi-fns)
                                      :validi?      false
                                      :koskettu?    false
                                      :tarkistettu? false}))
                    {}
                    (partition 2 kentat-ja-validaatiot))
            :validi? false
            :validoi validoi-fn
            :koske koske!))


(defn- optiot? 
  [m]
  (or (contains? m :viestit)
      (contains? m :arvo)
      (contains? m :tarkista-validointi-avaimella)))

(defn- testit? 
  [m]
  (and (contains? m :testi)
       (contains? m :virheviesti)))

(s/def ::tee-virheviesti-args (s/cat :tila map? :optiot (s/? optiot?) :testit (s/* testit?)))

(def virheviestit {::loppu-ennen-alkupvm "Loppupäivämäärä ennen alkupäivämäärää"
                   ::alku-jalkeen-loppupvm "Alkupäivämäärä loppupäivämäärän jälkeen"
                   ::ei-tyhja "Kenttä ei saa olla tyhjä"})

(defn tee-virheviesti
  "
  Spec:
  [lomaketila testi1 testi2 testi3 ...]  
  [lomaketila optiot testi1 testi2 testi3 ...]
  
  Optiot on valinnainen mappi, johon voi laittaa:
  :viestit - sisältää mapin joka mergetetään default virheviestien päälle - voidaan siis ylikirjoittaa tiettyjä virheviestejä, tai lisätä omia spesifisiä.

  Härveli ottaa sisään tilamapin, jossa on lomakkeen tiedot, sekä valinnaisen optiomapin.  
  Sitten härveli ottaa sisään mappeja joissa on virhetestejä. Validointi näyttää vain, 
  onko virhe olemassa, mutta se ei suoraan tiedä, mikä virhe siellä on.  Tämä härveli 
  tekee sen tarkistuksen ja hakee soveltuvan virheviestin. Virheviestit palautuvat vektorissa,
  jos niitä on.

  Testimapit koostuvat avaimista
  
  :testi - funktio jolle passataan arvo. Arvo on oletuksena tila, paitsi jos :arvoavain on annettu
  jos arvoavain löytyy, on kyseessä sitä vastaava arvo tilasta.  Testin palautusarvo on truthy jos 
  virhettä ei ole ja nil jos virhe löytyy. Ajatuksena siis, että tämä toimii samalla tavalla kuin 
  hoito-urakoissa käytetty validointihärveli ja niitä voisi sitten mix n matchata. 

  Huom. testit palauttavat siis arvon, kun kaikki on hyvin.

  Voi myös laittaa useamman funktion vektorissa/setissä, jolloin niistä tehdään kompositiofunktio.
  (Esim. ei-tyhja ja ei-nil)
  
  :virheviesti - avain virheviestit-mappiin, joka sisältää virheviestejä. Jos testistä tulee 
  nil, lisätään testiä vastaava viesti palautettavaan vektoriin.
  
  :arvo - valinnainen. jos tämän antaa, niin sitten haetaan arvo tilan polusta, joka on annettu
  arvolle vektorina. Voi antaa myös yksittäisen keywordin, se wrapataan vektoriin."
  [& parametrit]
  (let [{{:keys [viestit tarkista-validointi-avaimella] globaali-arvo :arvo} :optiot 
         :keys [tila testit] :as invalid?} (s/conform ::tee-virheviesti-args parametrit)
        _ (when (= ::s/invalid invalid?) 
            (loki/error (str "VIRHE: Virhe luotaessa virheilmoitusta. " (s/explain ::tee-virheviesti-args parametrit))))
        virheviestit (merge virheviestit viestit)
        xform (comp (map (fn [t] 
                           (let [{:keys [testi arvo virheviesti]} t
                                 testi (if (or (set? testi)
                                               (vector? testi))
                                         (apply comp testi)
                                         testi)
                                 arvo (cond 
                                        (or (keyword? arvo)
                                            (string? arvo)) 
                                        (get-in tila [arvo])
                                        
                                        (vector? arvo)
                                        (get-in tila arvo)
                                        
                                        (some? globaali-arvo)
                                        (get-in tila (if (vector? globaali-arvo) 
                                                       globaali-arvo
                                                       [globaali-arvo]))

                                        :else tila)] 
                             (when-not (testi arvo) 
                               (virheviesti virheviestit)))))
                    (keep identity))
        naytetaan-virhe? (cond 
                           tarkista-validointi-avaimella 
                           (validointi/nayta-virhe? tarkista-validointi-avaimella tila) 
                            
                           :else true)
        virheet (when naytetaan-virhe? 
                  (into [] xform testit))]
    (when-not (empty? virheet) virheet)))

(def kulun-oletus-validoinnit
  [[:koontilaskun-kuukausi] (:kulut/koontilaskun-kuukausi validoinnit)
   [:erapaiva] (:kulut/erapaiva validoinnit)
   [:laskun-numero] (:kulut/laskun-numero validoinnit)])

(defn kulun-validointi-meta
  ([kulu]
   (kulun-validointi-meta kulu {}))
  ([{:keys [kohdistukset] :as _kulu} opts]
   (apply luo-validius-tarkistukset
          (concat kulun-oletus-validoinnit
                  (mapcat (fn [i]
                            (if (= "lisatyo"
                                   (:maksueratyyppi
                                     (get kohdistukset i)))
                              [[:kohdistukset i :summa] (:kulut/summa validoinnit)
                               [:kohdistukset i :lisatyon-lisatieto] (:kulut/lisatyon-lisatieto validoinnit)
                               [:kohdistukset i :toimenpideinstanssi] (:kulut/toimenpideinstanssi validoinnit)]
                              [[:kohdistukset i :summa] (:kulut/summa validoinnit)
                               [:kohdistukset i :tehtavaryhma] (:kulut/tehtavaryhma validoinnit)]))
                          (range (count kohdistukset)))))))

(def kulut-kohdistus-default {:tehtavaryhma        nil
                              :toimenpideinstanssi nil
                              :summa               0
                              :poistettu           false
                              :lisatyo?            false
                              :rivi                0})

(def kulut-lomake-default (with-meta {:kohdistukset          [kulut-kohdistus-default]
                                      :aliurakoitsija        nil
                                      :koontilaskun-kuukausi nil
                                      :laskun-numero         nil
                                      :lisatieto             nil
                                      :suorittaja-nimi       nil
                                      :erapaiva              nil
                                      :liitteet              []
                                      :paivita               0}
                                     (kulun-validointi-meta {:kohdistukset [{}]})))

(def toteumat-default-arvot {:maarien-toteumat {:toimenpiteet-lataa true
                                                :syottomoodi           false
                                                :toimenpiteet          nil
                                                :toteutuneet-maarat    nil
                                                :valittu-toimenpide    {:otsikko "Kaikki" :id 0}
                                                :hakufiltteri          {:maaramitattavat true
                                                                        :rahavaraukset   true
                                                                        :lisatyot        true}
                                                :hoitokauden-alkuvuosi (if (>= (pvm/kuukausi (pvm/nyt)) 10)
                                                                         (pvm/vuosi (pvm/nyt))
                                                                         (dec (pvm/vuosi (pvm/nyt))))
                                                :aikavali-alkupvm      nil
                                                :aikavali-loppupvm     nil
                                                :lomake                {::t/toimenpide nil
                                                                        ::t/tyyppi     nil
                                                                        ::t/pvm        (pvm/nyt)
                                                                        ::t/toteumat   [{::t/tehtava            nil
                                                                                         ::t/toteuma-id         nil
                                                                                         ::t/ei-sijaintia       true
                                                                                         ::t/toteuma-tehtava-id nil
                                                                                         ::t/lisatieto          nil
                                                                                         ::t/maara              nil}]}}
                             :velho-varusteet {:valinnat {:hoitokauden-alkuvuosi nil
                                                          :hoitovuoden-kuukausi nil
                                                          :kuntoluokat nil
                                                          :varustetyypit nil
                                                          :toteuma nil}
                                               :varusteet []}})

(def paikkaus-default-arvot {:paikkauskohteet {:valitut-tilat #{"Kaikki"}
                                               :valittu-vuosi (pvm/vuosi (pvm/nyt)) ;; Kuluva vuosi
                                               :valitut-tyomenetelmat #{"Kaikki"}
                                               :valitut-elyt #{0}
                                               :paikkauskohteet? true
                                               :pot-jarjestys :tila
                                               :urakka-tila {:valittu-urakan-vuosi (pvm/vuosi (pvm/nyt))}
                                               }
                             :paikkaustoteumat {:valinnat {:aikavali (pvm/paivamaaran-hoitokausi (pvm/nyt))
                                                           :valitut-tyomenetelmat #{"Kaikki"}}
                                                :itemit-avain :paikkaukset
                                                :aikavali-otsikko "Tilauspäivämäärä"
                                                :voi-valita-trn-kartalta? false
                                                :palvelukutsu :hae-urakan-paikkaukset
                                                :palvelukutsu-tunniste :hae-paikkaukset-toteumat-nakymaan}})

(def kustannusten-seuranta-default-arvot {:kustannukset
                                          {:hoitokauden-alkuvuosi (if (>= (pvm/kuukausi (pvm/nyt)) 10)
                                                                    (pvm/vuosi (pvm/nyt))
                                                                    (dec (pvm/vuosi (pvm/nyt))))
                                           :kattohinta {:grid {}
                                                        :virheet {}}
                                           :valittu-kuukausi "Kaikki"
                                           :tavoitehinnan-oikaisut {}
                                           :valikatselmus-auki? false
                                           :valittu-urakka @nav/valittu-urakka-id}})

(defonce toteumanakyma (atom toteumat-default-arvot))
(def kustannusten-seuranta-nakymassa? (atom false))

(def kulut-default {:parametrit  {:haetaan 0
                                  :haun-kuukausi (pvm/kuukauden-aikavali (pvm/nyt))}
                    :taulukko    nil
                    :lomake      kulut-lomake-default
                    :kulut       []
                    :syottomoodi false})

(def laskutus-default {:kohdistetut-kulut kulut-default})
(def lupaukset-default {})
(def laatupoikkeamat-default {:listaus-tyyppi :kaikki
                              :hoitokauden-alkuvuosi (pvm/hoitokauden-alkuvuosi-nykyhetkesta (pvm/nyt))
                              :valittu-hoitokausi [(pvm/hoitokauden-alkupvm (pvm/hoitokauden-alkuvuosi-nykyhetkesta (pvm/nyt)))
                                                   (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc (pvm/hoitokauden-alkuvuosi-nykyhetkesta (pvm/nyt)))))]
                              :valittu-aikavali [(pvm/hoitokauden-alkupvm (pvm/hoitokauden-alkuvuosi-nykyhetkesta (pvm/nyt)))
                                                 (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc (pvm/hoitokauden-alkuvuosi-nykyhetkesta (pvm/nyt)))))]})
(def pot2-default-arvot {:massat nil
                         :pot2-massa-lomake nil
                         :pot2-lomake nil})

(defonce tila (atom {:yleiset     {:urakka {}}
                     :laatupoikkeamat laatupoikkeamat-default
                     :laskutus    laskutus-default
                     :lupaukset lupaukset-default
                     :pot2 pot2-default-arvot
                     :suunnittelu suunnittelu-default-arvot
                     :toteumat    toteumat-default-arvot
                     :paikkaukset paikkaus-default-arvot
                     :kustannusten-seuranta kustannusten-seuranta-default-arvot}))

(defonce laatupoikkeamat (cursor tila [:laatupoikkeamat]))
(defonce paikkauskohteet (cursor tila [:paikkaukset :paikkauskohteet]))
(defonce paikkaustoteumat (cursor tila [:paikkaukset :paikkaustoteumat]))
(defonce paikkauspaallystykset (cursor tila [:paikkaukset :paallystysilmoitukset]))

(defonce pot2 (atom pot2-default-arvot))

(defonce kustannusten-seuranta (cursor tila [:kustannusten-seuranta :kustannukset]))
(defonce maarien-toteumat (cursor tila [:toteumat :maarien-toteumat]))
(defonce velho-varusteet (cursor tila [:toteumat :velho-varusteet]))

(defonce laskutus-kohdistetut-kulut (cursor tila [:laskutus :kohdistetut-kulut]))

(defonce lupaukset (cursor tila [:lupaukset]))

(defonce yleiset (cursor tila [:yleiset]))

(defonce suunnittelu-tehtavat (cursor tila [:suunnittelu :tehtavat]))

(defonce suunnittelu-kustannussuunnitelma (cursor tila [:suunnittelu :kustannussuunnitelma]))
(defonce kustannussuunnitelma-kattohinta (cursor suunnittelu-kustannussuunnitelma [:kattohinta]))

(defonce tavoitehinnan-oikaisut (cursor tila [:kustannusten-seuranta :kustannukset :tavoitehinnan-oikaisut]))

(defonce toteumat-maarien-toteumat (atom {:maarien-toteumat {:toimenpiteet          nil
                                                             :toteutuneet-maarat    nil
                                                             :hoitokauden-alkuvuosi (if (>= (pvm/kuukausi (pvm/nyt)) 10)
                                                                                      (pvm/vuosi (pvm/nyt))
                                                                                      (dec (pvm/vuosi (pvm/nyt))))
                                                             :aikavali-alkupvm      nil
                                                             :aikavali-loppupvm     nil
                                                             :toteuma               {:toimenpide         nil
                                                                                     :tehtava            nil
                                                                                     :toteuma-id         nil
                                                                                     :toteuma-tehtava-id nil
                                                                                     :lisatieto          nil
                                                                                     :maara              nil
                                                                                     :loppupvm           (pvm/nyt)}
                                                             :syottomoodi           false}}))

;; FIXME: Tästä pitäisi päästä eroon kokonaan. Tuckin, atomien ja watchereiden käyttö yhdessä aiheuttaa välillä hankalasti selviteltäviä
;;        tilan mutatointiin liittyviä bugeja esimerkiksi reagentin lifcycle metodeja käyttäessä.
(add-watch nav/valittu-urakka :urakan-id-watch
           (fn [_ _ _ uusi-urakka]
             (doseq [f! @urakan-vaihto-triggerit]
               (f!))
             (when-not (= 0 (count @urakan-vaihto-triggerit))
               (reset! urakan-vaihto-triggerit []))
             (swap! tila (fn [tila]
                           (-> tila
                               (assoc-in [:yleiset :urakka] (dissoc uusi-urakka :alue))
                             ;; NOTE: Disabloitu, koska VHAR-4909. Tämä resetoi kustannussuunnitelman tilan ennen kuin un-mount on ehtinyt suorittua
                             ;;       ja kustannussuunnitelman gridit jäävät täten siivoamatta.
                               #_(assoc :suunnittelu suunnittelu-default-arvot))))
             ;dereffataan kursorit, koska ne on laiskoja
             ;; NOTE: Disabloitu, koska VHAR-4909
             #_@suunnittelu-kustannussuunnitelma))
