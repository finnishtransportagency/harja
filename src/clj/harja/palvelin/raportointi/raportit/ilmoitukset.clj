(ns harja.palvelin.raportointi.raportit.ilmoitukset
  "Ilmoitus välilehden raportti"
  (:require
   [harja.palvelin.raportointi.raportit.yleinen :as yleinen :refer [rivi]]
   [harja.domain.ely :as ely]
   [harja.domain.tierekisteri :as tr-domain]
   [harja.pvm :as pvm]
   [taoensso.timbre :as log]
   [harja.domain.tieliikenneilmoitukset :refer
    [+ilmoitusten-selitteet+ tilan-selite] :as domain]))

(defn- rivi-taulukolle
  [tiedot lihavoi?]
  (let [ilmoitustyyppi (domain/ilmoitustyypin-lyhenne (:ilmoitustyyppi tiedot))
        tietojen-selitteet-koko (count (:selitteet tiedot))
        ;; Rivittää lisätieto-kentän joka 90. kirjaimen seuraavalle riville, jos lisätiedossa paljon tekstiä 
        lisatieto-rivitetty (clojure.string/replace (str (:lisatieto tiedot)) #"(.{90})(?!$)" "$1\n")
        ;; Käydään läpi selitteet, lisää pilkun jos enemmän selitteitä olemassa
        ;; "Savea tiellä, Vettä tiellä"
        tietojen-selitteet (map (fn [x]
                                  (let [indeksi (.indexOf (:selitteet tiedot) x)
                                        valittaja (when (not= (+ indeksi 1) tietojen-selitteet-koko) ", ")]
                                    (str (x +ilmoitusten-selitteet+) valittaja))) (:selitteet tiedot))]
    (rivi
     ;; Urakka
     [:varillinen-teksti {:arvo (str (:urakkanimi tiedot)) :lihavoi? lihavoi?}]
     ;; Saapunut
     [:varillinen-teksti {:arvo (pvm/pvm-aika (:valitetty tiedot)) :lihavoi? lihavoi?}]
     ;; Tyyppi 
     [:varillinen-teksti {:kustomi-tyyli ilmoitustyyppi :arvo ilmoitustyyppi :lihavoi? lihavoi?}]
     ;; Selite
     [:varillinen-teksti {:arvo (apply str tietojen-selitteet) :lihavoi? lihavoi?}]
     ;; Lisätieto 
     [:varillinen-teksti {:arvo lisatieto-rivitetty :lihavoi? lihavoi?}]
     ;; Tie 
     [:varillinen-teksti {:arvo (tr-domain/tierekisteriosoite-tekstina (:tr tiedot) {:teksti-tie? false}) :lihavoi? lihavoi?}]
     ;; Tila 
     [:varillinen-teksti {:arvo ((:tila tiedot) tilan-selite) :lihavoi? lihavoi?}]
     ;; Toimenpiteet aloitettu 
     [:varillinen-teksti {:arvo (pvm/pvm-aika (:toimenpiteet-aloitettu tiedot)) :lihavoi? lihavoi?}])))

(defn- taulukko [{:keys [otsikot tiedot]}]
  (let [rivit (into []
                    (remove nil?
                            (for [x tiedot]
                              (rivi-taulukolle x true))))]

    [:taulukko {:viimeinen-rivi-yhteenveto? false}
     (let [testets (map (fn [x]
                          {:otsikko x
                           :leveys 36}) otsikot)]

       ;; TODO en tiedä voiko tätä jotenkin nätimmin tehdä
       (rivi
        (first testets)
        (second testets)
        (nth testets 2)
        (nth testets 3)
        (nth testets 4)
        (nth testets 5)
        (nth testets 6)
        (nth testets 7))) rivit]))

(defn- filtterit-rivi
  ([o1 o2 o3 o4 lihavoi?]
   (rivi
    [:varillinen-teksti {:arvo (str o1) :lihavoi? lihavoi?}]
    [:varillinen-teksti {:arvo (str o2) :lihavoi? lihavoi?}]
    [:varillinen-teksti {:arvo (str o3) :lihavoi? lihavoi?}]
    [:varillinen-teksti {:arvo (str o4) :lihavoi? lihavoi?}]))
  ([_]
   (rivi
    [:varillinen-teksti {:arvo ""}]
    [:varillinen-teksti {:arvo ""}]
    [:varillinen-teksti {:arvo ""}]
    [:varillinen-teksti {:arvo ""}])))

(defn filtteri-paalla-str [filtteri]
  (if filtteri "✓" "-"))

(defn filtteri-selitys-str [filtteri]
  (if (and filtteri (> (count filtteri) 0)) (str filtteri) "-"))

(defn- filtterit-taulukko [{:keys [sheet-nimi valitetty-urakkaan-vakioaikavali
                                   toimenpiteet-aloitettu-vakioaikavali hakuehto
                                   selite tr-numero tunniste ilmoittaja-nimi ilmoittaja-puhelin
                                   tilat tyypit vaikutukset aloituskuittauksen-ajankohta]}]

  (let [valitetty-urakkaan-vakioaikavali (str (:nimi valitetty-urakkaan-vakioaikavali))
        toimenpiteet-aloitettu-vakioaikavali (str (:nimi toimenpiteet-aloitettu-vakioaikavali))
        hakuehto (filtteri-selitys-str hakuehto)
        selite (filtteri-selitys-str (second selite))
        tr-numero (filtteri-selitys-str tr-numero)
        tunniste (filtteri-selitys-str tunniste)
        ilmoittaja-nimi (filtteri-selitys-str ilmoittaja-nimi)
        ilmoittaja-puhelin (filtteri-selitys-str ilmoittaja-puhelin)

        kuittaamaton (filtteri-paalla-str (contains? tilat :kuittaamaton))
        vastaanotettu (filtteri-paalla-str (contains? tilat :vastaanotettu))
        aloitettu (filtteri-paalla-str (contains? tilat :aloitettu))
        lopetettu (filtteri-paalla-str (contains? tilat :lopetettu))

        TPP (filtteri-paalla-str (contains? tyypit :toimenpidepyynto))
        TUR (filtteri-paalla-str (contains? tyypit :tiedoitus))
        URK (filtteri-paalla-str (contains? tyypit :kysely))

        aloituskuittauksen-ajankohta (cond
                                       (= aloituskuittauksen-ajankohta :kaikki)
                                       "Älä rajoita aloituskuittauksella"
                                       (= aloituskuittauksen-ajankohta :alle-tunti)
                                       "Alle tunnin kuluessa"
                                       (= aloituskuittauksen-ajankohta :myohemmin)
                                       "Yli tunnin päästä")

        myohassa (filtteri-paalla-str (contains? vaikutukset :myohassa))
        aiheutti-toimenpiteita (filtteri-paalla-str (contains? vaikutukset :aiheutti-toimenpiteita))

        rivit (into []
                    [(filtterit-rivi "Tiedotettu urakkaan" "Toimenpiteet aloitettu" nil nil true)
                     (filtterit-rivi valitetty-urakkaan-vakioaikavali toimenpiteet-aloitettu-vakioaikavali nil nil false)

                     (filtterit-rivi "Hakusana" "Selite" "Tienumero" "Tunniste" true)
                     (filtterit-rivi hakuehto selite tr-numero tunniste false)

                     (filtterit-rivi "Ilmoittaja" "Ilmoittajan puh" nil nil true)
                     (filtterit-rivi ilmoittaja-nimi ilmoittaja-puhelin nil nil false)

                     (filtterit-rivi "Kuittaamaton" "Vastaanotettu" "Aloitettu" "Lopetettu" true)
                     (filtterit-rivi kuittaamaton vastaanotettu aloitettu lopetettu false)

                     (filtterit-rivi "TPP" "TUR" "URK" nil true)
                     (filtterit-rivi TPP TUR URK nil false)

                     (filtterit-rivi "Myöhästyneet ilmoitukset" "Toimenpiteitä aiheuttaneet" "Aloituskuittaus annettu" nil true)
                     (filtterit-rivi myohassa aiheutti-toimenpiteita aloituskuittauksen-ajankohta nil false)])]

    [:taulukko {:piilota-border? true
                :sheet-nimi sheet-nimi
                :hoitokausi-arvotaulukko? false
                :viimeinen-rivi-yhteenveto? false}
     (rivi
      {:otsikko "Käytetyt filtterit" :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 12 :tyyppi :varillinen-teksti}
      {:otsikko " " :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 15 :tyyppi :varillinen-teksti}
      {:otsikko " " :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 15 :tyyppi :varillinen-teksti}
      {:otsikko " " :otsikkorivi-luokka "otsikko-ei-taustaa" :leveys 15 :tyyppi :varillinen-teksti}) rivit]))

(defn suorita [_ _ {:keys [tiedot urakka hallintayksikko filtterit parametrit] }]

  (let [;; Voiko destruktoida funktion parametreissa? Miten?
        tiedot (:tiedot parametrit)
        urakka (:urakka parametrit)
        hallintayksikko (:hallintayksikko parametrit)
        filtterit (:filtterit parametrit)
        
        otsikot ["Urakka" "Saapunut"
                 "Tyyppi" "Selite"
                 "Lisätieto" "Tie"
                 "Tila" "Toimenpiteet aloitettu"]

        valittu-ely (try (get ely/elynumero->nimi (Long/parseLong (:elynumero hallintayksikko))) (catch Throwable _ nil))

        otsikko (if-not (:nimi urakka)
                  (str "Ilmoitukset, " valittu-ely) "Ilmoitukset")

        otsikko (if (and (not (:nimi urakka)) (not valittu-ely)) (str "Ilmoitukset, Koko maa") otsikko)

        valinnat (:valinnat filtterit)
        alkuaika (:valitetty-urakkaan-alkuaika valinnat)
        tyypit (:tyypit valinnat)
        ilmoittaja-nimi (:ilmoittaja-nimi valinnat)
        hakuehto (:hakuehto valinnat)
        ilmoittaja-puhelin (:ilmoittaja-puhelin valinnat)
        toimenpiteet-aloitettu-vakioaikavali (:toimenpiteet-aloitettu-vakioaikavali valinnat)
        selite (:selite valinnat)
        tilat (:tilat valinnat)
        valitetty-urakkaan-vakioaikavali (:valitetty-urakkaan-vakioaikavali valinnat)
        tr-numero (:tr-numero valinnat)
        tunniste (:tunniste valinnat)
        vaikutukset (:vaikutukset valinnat)
        aloituskuittauksen-ajankohta (:aloituskuittauksen-ajankohta valinnat)]

    [:raportti {:nimi otsikko
                :piilota-otsikko? true}

     ;; Halutaan säilyttää frontin oma html, mutta generoidaan excelit ja pdf 
     [:piilota-html
      (filtterit-taulukko {:data nil
                           :alkuaika alkuaika
                           :valitetty-urakkaan-vakioaikavali valitetty-urakkaan-vakioaikavali
                           :toimenpiteet-aloitettu-vakioaikavali toimenpiteet-aloitettu-vakioaikavali
                           :hakuehto hakuehto
                           :selite selite
                           :tr-numero tr-numero
                           :tunniste tunniste
                           :ilmoittaja-nimi ilmoittaja-nimi
                           :ilmoittaja-puhelin ilmoittaja-puhelin
                           :tilat tilat
                           :tyypit tyypit
                           :vaikutukset vaikutukset
                           :aloituskuittauksen-ajankohta aloituskuittauksen-ajankohta
                           :sheet-nimi "ilmoitukset"
                           :otsikko "Filtterit"})

      (taulukko {:otsikot otsikot
                 :tiedot tiedot
                 :sheet-nimi "ilmoitukset"})]]))
