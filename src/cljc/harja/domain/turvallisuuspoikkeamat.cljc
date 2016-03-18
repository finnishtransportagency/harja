(ns harja.domain.turvallisuuspoikkeamat)

(def turpo-tyypit {:tyotapaturma "Työtapaturma"
                   :vaaratilanne "Vaaratilanne"
                   :turvallisuushavainto "Turvallisuushavainto"
                   :muu "Muu"})

(def vahinkoluokittelu-tyypit {:henkilovahinko   "Henkilövahinko"
                               :omaisuusvahinko  "Omaisuusvahinko"
                               :ymparistovahinko "Ympäristövahinko"})

(def turpo-vakavuusasteet {:lieva "Lievä"
                           :vakava "Vakava"})

(defn henkilovahingon-checkboksien-avaimet-jarjestyksessa [map]
  (-> map
      keys
      sort
      (->> (remove #{:ei_tietoa}))
      (conj :ei_tietoa)))

(def vammat
  {:haavat_ja_pinnalliset_vammat "Haavat ja pinnalliset vammat"
   :luunmurtumat "Luunmurtumat"
   :sijoiltaan_menot_nyrjahdykset_ja_venahdykset "Sijoiltaan menot nyrjähdykset ja venähdykset"
   :amputoitumiset_ja_irti_repeamiset "Amputoitumiset ja irti repeämiset (ruumiinosan menetys)"
   :tarahdykset_ja_sisaiset_vammat_ruhjevammat "Tärähdykset ja sisäiset vammat ruhjevammat"
   :palovammat_syopymat_ja_paleltumat "Palovammat syöpymät ja paleltumat"
   :myrkytykset_ja_tulehdukset "Myrkytykset ja tulehdukset"
   :hukkuminen_ja_tukehtuminen "Hukkuminen ja tukehtuminen"
   :aanen_ja_varahtelyn_vaikutukset "Äänen ja värähtelyn vaikutukset"
   :aarilampotilojen_valon_ja_sateilyn_vaikutukset "Äärilämpötilojen valon ja säteilyn vaikutukset"
   :sokki "Sokki"
   :useita_samantasoisia_vammoja "Useita samantasoisia vammoja"
   :muut "Muut"
   :ei_tietoa "Ei tietoa"})

(def vammat-avaimet-jarjestyksessa
  (henkilovahingon-checkboksien-avaimet-jarjestyksessa vammat))

(def vahingoittunut-ruumiinosa
  {:paan_alue "Pään alue (pl. silmät)"
   :silmat "Silmä(t)"
   :niska_ja_kaula "Niska ja kaula"
   :selka "Selkä"
   :vartalo "Vartalo, mukaan lukien sisäelimet"
   :sormi_kammen "Sormi (sormet), kämmen"
   :ranne "Ranne"
   :muu_kasi "Muu käsi, mukaan lukien olkapää"
   :nilkka "Nilkka"
   :jalkatera_ja_varvas "Jalkaterä ja varvas (varpaat)"
   :muu_jalka "Muu jalka, mukaan lukien lonkka ja nivuset"
   :koko_keho "Koko keho (useat kehon alueet)"
   :ei_tietoa "Ei tietoa"})

(def vahingoizttunut-ruumiinosa-avaimet-jarjestyksessa
  (henkilovahingon-checkboksien-avaimet-jarjestyksessa vahingoittunut-ruumiinosa))