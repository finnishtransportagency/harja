(ns harja.tiedot.urakka.pot2.validoinnit

  (:require
    [harja.domain.pot2 :as pot2-domain]))


(defn- pakolliset-runkoaineen-kentat [tyyppi]
  (case tyyppi
    (1 2 4 5 6)
    #{:runkoaine/esiintyma
      :runkoaine/kuulamyllyarvo
      :runkoaine/litteysluku
      :runkoaine/massaprosentti}
    3
    #{:runkoaine/fillerityyppi
      :runkoaine/massaprosentti}

    7 #{:runkoaine/kuvaus
        :runkoaine/massaprosentti}))


(defn- muotoile-ainetyypin-puutteet [puutteet ainetyypit ainetyypin-str]
  (apply str
         (map
           (fn [[tyyppi kentat]]
             (when-not (empty? kentat)
               (str ainetyypin-str
                    (::pot2-domain/nimi (first
                                          (filter #(= (::pot2-domain/koodi %) tyyppi)
                                                  ainetyypit)))
                    " puuttuu kentat: " (clojure.string/join ", " kentat) ". ")))
           puutteet)))


(defn- runkoaineiden-validointipuutteet
  [runkoaineet runkoainetyypit]
  (if (empty? runkoaineet)
    "Valitse ainakin yksi runkoaine"
    (let [puutteet (into {}
                         (map (fn [[tyyppi aine]]
                                {tyyppi
                                 (keep (fn [kentta]
                                         (when (and (:valittu? aine)
                                                    (nil? (aine kentta)))
                                           (name kentta)))
                                       (pakolliset-runkoaineen-kentat tyyppi))})
                              runkoaineet))
          puutteet-luettavaksi (muotoile-ainetyypin-puutteet puutteet runkoainetyypit "Runkoaineesta ")]
      (if (empty? puutteet-luettavaksi)
        nil
        puutteet-luettavaksi))))

(defn- hae-sideaineen-puutteet [aineet]
  (into {}
        (map (fn [[_ aine]]
               (do
                 {(:sideaine/tyyppi aine)
                  (keep (fn [kentta]
                          (when (nil? (aine kentta))
                            (name kentta)))
                        #{:sideaine/tyyppi :sideaine/pitoisuus})}))
             aineet)))


(defn- sideaineiden-validointipuutteet
  [sideaineet sideainetyypit]
  (if (or (empty? sideaineet)
          (and
            (or
              (get-in sideaineet [:lopputuote :valittu?])
              (get-in sideaineet [:lisatty :valittu?]))
            (and (empty? (get-in sideaineet [:lopputuote :aineet]))
                 (empty? (get-in sideaineet [:lisatty :aineet])))))
    "Valitse ainakin yksi sideaine"
    (let [lopputuotteen-aineet (when (:valittu? (:lopputuote sideaineet))
                                 (get-in sideaineet [:lopputuote :aineet]))
          lisatyt-aineet (when (:valittu? (:lisatty sideaineet))
                           (get-in sideaineet [:lisatty :aineet]))
          lopputuotteen-puutteet (hae-sideaineen-puutteet lopputuotteen-aineet)
          lisattyjen-puutteet (hae-sideaineen-puutteet lisatyt-aineet)
          lopputuotteen-puutteet-luettavaksi (muotoile-ainetyypin-puutteet lopputuotteen-puutteet sideainetyypit "Lopputuotteen sideaineesta ")
          lisattyjen-puutteet-luettavaksi (muotoile-ainetyypin-puutteet lisattyjen-puutteet sideainetyypit "Lisätystä sideaineesta ")]
      (if (and (empty? lopputuotteen-puutteet-luettavaksi)
               (empty? lisattyjen-puutteet-luettavaksi))
        nil
        (str lopputuotteen-puutteet-luettavaksi
             lisattyjen-puutteet-luettavaksi)))))

(defn runko-ja-sideaineen-validointivirheet
  [{::pot2-domain/keys [runkoaineet sideaineet lisaaineet] :as lomake}
   {:keys [runkoainetyypit sideainetyypit] :as materiaalikoodistot}]
  (let [runkoaineiden-puutteet (runkoaineiden-validointipuutteet runkoaineet runkoainetyypit)
        sideaineiden-puutteet (sideaineiden-validointipuutteet sideaineet sideainetyypit)
        ; TODO: lisäaineiden validointi vielä..
        ]
    (remove nil? [runkoaineiden-puutteet sideaineiden-puutteet])))