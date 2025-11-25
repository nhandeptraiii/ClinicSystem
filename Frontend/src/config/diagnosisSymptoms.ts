export type SymptomItem = {
  value: string;
  labelVi: string;
  labelEn?: string;
};

export type SymptomGroupId =
  | 'respiratory'
  | 'ent'
  | 'cardio'
  | 'gi'
  | 'gu'
  | 'musculoskeletal'
  | 'dermatology'
  | 'neuro'
  | 'general';

export type SymptomGroup = {
  id: SymptomGroupId;
  labelVi: string;
  descriptionVi?: string;
  symptoms: SymptomItem[];
};

const toTitleCase = (text: string) =>
  text.replace(/\w\S*/g, (word) => word.charAt(0).toUpperCase() + word.slice(1));

const groupMeta: Record<SymptomGroupId, { labelVi: string; descriptionVi?: string }> = {
  respiratory: {
    labelVi: 'Hô hấp',
    descriptionVi: 'Các triệu chứng hô hấp, dễ gặp trong cảm lạnh, viêm phổi, hen, covid...',
  },
  ent: {
    labelVi: 'Tai mũi họng',
    descriptionVi: 'Tai, mũi, họng, mắt và vùng đầu mặt cổ.',
  },
  cardio: {
    labelVi: 'Tim mạch / tuần hoàn',
    descriptionVi: 'Triệu chứng liên quan tim mạch, đau ngực, phù chân tay.',
  },
  gi: {
    labelVi: 'Tiêu hóa',
    descriptionVi: 'Dạ dày - ruột, gan tụy, đại tràng.',
  },
  gu: {
    labelVi: 'Tiết niệu – sinh dục',
    descriptionVi: 'Thận, bàng quang, cơ quan sinh dục, thai kỳ.',
  },
  musculoskeletal: {
    labelVi: 'Cơ xương khớp',
    descriptionVi: 'Đau, cứng, sưng cơ xương khớp, vận động.',
  },
  dermatology: {
    labelVi: 'Da liễu / dị ứng',
    descriptionVi: 'Phát ban, sưng da, kích ứng, dị ứng.',
  },
  neuro: {
    labelVi: 'Thần kinh – tâm thần',
    descriptionVi: 'Đau đầu, chóng mặt, giấc ngủ, hành vi, cảm xúc.',
  },
  general: {
    labelVi: 'Toàn thân / chung',
    descriptionVi: 'Triệu chứng toàn thân như sốt, mệt, chán ăn.',
  },
};

type RawSymptom = SymptomItem & { groupId: SymptomGroupId };

const rawSymptoms: RawSymptom[] = [
  { value: 'throat feels tight', labelVi: 'Căng tức họng', groupId: 'ent' },
  { value: 'lump in throat', labelVi: 'Cảm giác vướng ở họng', groupId: 'ent' },
  { value: 'swollen lymph nodes', labelVi: 'Hạch sưng', groupId: 'general' },
  { value: 'bones are painful', labelVi: 'Đau xương', groupId: 'musculoskeletal' },
  { value: 'muscle pain', labelVi: 'Đau cơ', groupId: 'musculoskeletal' },
  { value: 'skin swelling', labelVi: 'Sưng da', groupId: 'dermatology' },
  { value: 'jaw swelling', labelVi: 'Sưng hàm', groupId: 'ent' },
  { value: 'diminished hearing', labelVi: 'Nghe kém', groupId: 'ent' },
  { value: 'vomiting', labelVi: 'Nôn ói', groupId: 'gi' },
  { value: 'cough', labelVi: 'Ho', groupId: 'respiratory' },
  { value: 'retention of urine', labelVi: 'Bí tiểu', groupId: 'gu' },
  { value: 'hand or finger stiffness or tightness', labelVi: 'Cứng tay hoặc ngón tay', groupId: 'musculoskeletal' },
  { value: 'nausea', labelVi: 'Buồn nôn', groupId: 'gi' },
  { value: 'sharp abdominal pain', labelVi: 'Đau bụng nhói', groupId: 'gi' },
  { value: 'constipation', labelVi: 'Táo bón', groupId: 'gi' },
  { value: 'sore throat', labelVi: 'Đau rát họng', groupId: 'ent' },
  { value: 'lip swelling', labelVi: 'Sưng môi', groupId: 'dermatology' },
  { value: 'neck stiffness or tightness', labelVi: 'Cứng cổ', groupId: 'musculoskeletal' },
  { value: 'mouth dryness', labelVi: 'Khô miệng', groupId: 'ent' },
  { value: 'groin mass', labelVi: 'Khối vùng bẹn', groupId: 'gu' },
  { value: 'irritable infant', labelVi: 'Trẻ quấy khó chịu', groupId: 'general' },
  { value: 'wrist swelling', labelVi: 'Sưng cổ tay', groupId: 'musculoskeletal' },
  { value: 'back cramps or spasms', labelVi: 'Chuột rút/cứng lưng', groupId: 'musculoskeletal' },
  { value: 'headache', labelVi: 'Đau đầu', groupId: 'neuro' },
  { value: 'insomnia', labelVi: 'Mất ngủ', groupId: 'neuro' },
  { value: 'arm pain', labelVi: 'Đau cánh tay', groupId: 'musculoskeletal' },
  { value: 'problems with movement', labelVi: 'Khó vận động', groupId: 'neuro' },
  { value: 'shortness of breath', labelVi: 'Khó thở', groupId: 'respiratory' },
  { value: 'flatulence', labelVi: 'Đầy hơi, xì hơi nhiều', groupId: 'gi' },
  { value: 'foot or toe swelling', labelVi: 'Phù bàn chân/ngón chân', groupId: 'cardio' },
  { value: 'nasal congestion', labelVi: 'Ngạt mũi', groupId: 'ent' },
  { value: 'stiffness all over', labelVi: 'Cứng người toàn thân', groupId: 'musculoskeletal' },
  { value: 'palpitations', labelVi: 'Tim đập hồi hộp', groupId: 'cardio' },
  { value: 'painful urination', labelVi: 'Tiểu buốt', groupId: 'gu' },
  { value: 'fever', labelVi: 'Sốt', groupId: 'general' },
  { value: 'difficulty speaking', labelVi: 'Khó nói', groupId: 'neuro' },
  { value: 'anxiety and nervousness', labelVi: 'Lo âu, bồn chồn', groupId: 'neuro' },
  { value: 'lack of growth', labelVi: 'Chậm lớn', groupId: 'general' },
  { value: 'pain in testicles', labelVi: 'Đau tinh hoàn', groupId: 'gu' },
  { value: 'lower abdominal pain', labelVi: 'Đau bụng dưới', groupId: 'gi' },
  { value: 'heartburn', labelVi: 'Ợ nóng', groupId: 'gi' },
  { value: 'suprapubic pain', labelVi: 'Đau tức vùng trên xương mu', groupId: 'gu' },
  { value: 'irregular heartbeat', labelVi: 'Nhịp tim không đều', groupId: 'cardio' },
  { value: 'dizziness', labelVi: 'Chóng mặt', groupId: 'neuro' },
  { value: 'vaginal itching', labelVi: 'Ngứa âm đạo', groupId: 'gu' },
  { value: 'back pain', labelVi: 'Đau lưng', groupId: 'musculoskeletal' },
  { value: 'difficulty in swallowing', labelVi: 'Khó nuốt', groupId: 'ent' },
  { value: 'abnormal appearing skin', labelVi: 'Da trông bất thường', groupId: 'dermatology' },
  { value: 'swelling of scrotum', labelVi: 'Bìu sưng', groupId: 'gu' },
  { value: 'pelvic pain', labelVi: 'Đau vùng chậu', groupId: 'gu' },
  { value: 'side pain', labelVi: 'Đau hông/lườn', groupId: 'gu' },
  { value: 'itchy ear(s)', labelVi: 'Ngứa tai', groupId: 'ent' },
  { value: 'sharp chest pain', labelVi: 'Đau ngực nhói', groupId: 'cardio' },
  { value: 'mass in scrotum', labelVi: 'Khối ở bìu', groupId: 'gu' },
  { value: 'mouth ulcer', labelVi: 'Loét miệng', groupId: 'ent' },
  { value: 'burning abdominal pain', labelVi: 'Đau bụng rát', groupId: 'gi' },
  { value: 'chest tightness', labelVi: 'Tức ngực', groupId: 'respiratory' },
  { value: 'ache all over', labelVi: 'Đau nhức khắp người', groupId: 'general' },
  { value: 'skin lesion', labelVi: 'Tổn thương da', groupId: 'dermatology' },
  { value: 'intermenstrual bleeding', labelVi: 'Ra máu giữa kỳ', groupId: 'gu' },
  { value: 'arm swelling', labelVi: 'Sưng cánh tay', groupId: 'musculoskeletal' },
  { value: 'pain during intercourse', labelVi: 'Đau khi quan hệ', groupId: 'gu' },
  { value: 'skin rash', labelVi: 'Phát ban da', groupId: 'dermatology' },
  { value: 'foreign body sensation in eye', labelVi: 'Cảm giác cộm mắt', groupId: 'ent' },
  { value: 'skin growth', labelVi: 'Khối u/nhú trên da', groupId: 'dermatology' },
  { value: 'peripheral edema', labelVi: 'Phù ngoại biên', groupId: 'cardio' },
  { value: 'frequent urination', labelVi: 'Đi tiểu nhiều lần', groupId: 'gu' },
  { value: 'vaginal discharge', labelVi: 'Ra dịch âm đạo', groupId: 'gu' },
  { value: 'long menstrual periods', labelVi: 'Kinh kéo dài', groupId: 'gu' },
  { value: 'pain during pregnancy', labelVi: 'Đau khi mang thai', groupId: 'gu' },
  { value: 'foot or toe pain', labelVi: 'Đau bàn chân/ngón chân', groupId: 'musculoskeletal' },
  { value: 'coryza', labelVi: 'Sổ mũi/chảy nước mũi', groupId: 'ent' },
  { value: 'lower body pain', labelVi: 'Đau vùng thân dưới', groupId: 'musculoskeletal' },
  { value: 'disturbance of memory', labelVi: 'Giảm trí nhớ', groupId: 'neuro' },
  { value: 'hoarse voice', labelVi: 'Khàn tiếng', groupId: 'ent' },
  { value: 'temper problems', labelVi: 'Khó kiểm soát cảm xúc', groupId: 'neuro' },
  { value: 'pain of the anus', labelVi: 'Đau hậu môn', groupId: 'gi' },
  { value: 'hip pain', labelVi: 'Đau hông', groupId: 'musculoskeletal' },
  { value: 'neck pain', labelVi: 'Đau cổ', groupId: 'musculoskeletal' },
  { value: 'low back pain', labelVi: 'Đau thắt lưng', groupId: 'musculoskeletal' },
  { value: 'swollen eye', labelVi: 'Mắt sưng', groupId: 'ent' },
  { value: 'blood in urine', labelVi: 'Tiểu ra máu', groupId: 'gu' },
  { value: 'hand or finger swelling', labelVi: 'Sưng tay/ngón tay', groupId: 'musculoskeletal' },
  { value: 'diminished vision', labelVi: 'Nhìn mờ', groupId: 'ent' },
  { value: 'hand or finger pain', labelVi: 'Đau tay/ngón tay', groupId: 'musculoskeletal' },
  { value: 'loss of sensation', labelVi: 'Mất cảm giác', groupId: 'neuro' },
  { value: 'vaginal pain', labelVi: 'Đau âm đạo', groupId: 'gu' },
  { value: 'knee pain', labelVi: 'Đau gối', groupId: 'musculoskeletal' },
  { value: 'pain in eye', labelVi: 'Đau mắt', groupId: 'ent' },
  { value: 'chills', labelVi: 'Ớn lạnh', groupId: 'general' },
  { value: 'ear pain', labelVi: 'Đau tai', groupId: 'ent' },
  { value: 'ankle pain', labelVi: 'Đau mắt cá', groupId: 'musculoskeletal' },
  { value: 'spots or clouds in vision', labelVi: 'Đốm/mờ trong tầm nhìn', groupId: 'ent' },
  { value: 'changes in stool appearance', labelVi: 'Phân thay đổi bất thường', groupId: 'gi' },
  { value: 'joint pain', labelVi: 'Đau khớp', groupId: 'musculoskeletal' },
  { value: 'blood in stool', labelVi: 'Đi ngoài ra máu', groupId: 'gi' },
  { value: 'skin moles', labelVi: 'Nốt ruồi/sắc tố da', groupId: 'dermatology' },
  { value: 'eye redness', labelVi: 'Mắt đỏ', groupId: 'ent' },
  { value: 'lacrimation', labelVi: 'Chảy nước mắt', groupId: 'ent' },
  { value: 'increased heart rate', labelVi: 'Tim đập nhanh', groupId: 'cardio' },
  { value: 'elbow swelling', labelVi: 'Sưng khuỷu tay', groupId: 'musculoskeletal' },
  { value: 'abnormal breathing sounds', labelVi: 'Tiếng thở bất thường/khò khè', groupId: 'respiratory' },
  { value: 'diarrhea', labelVi: 'Tiêu chảy', groupId: 'gi' },
  { value: 'problems during pregnancy', labelVi: 'Vấn đề khi mang thai', groupId: 'gu' },
  { value: 'depressive or psychotic symptoms', labelVi: 'Triệu chứng trầm cảm/hoang tưởng', groupId: 'neuro' },
  { value: 'facial pain', labelVi: 'Đau vùng mặt', groupId: 'ent' },
  { value: 'vulvar irritation', labelVi: 'Kích ứng vùng âm hộ', groupId: 'gu' },
  { value: 'groin pain', labelVi: 'Đau vùng bẹn', groupId: 'gu' },
  { value: 'elbow pain', labelVi: 'Đau khuỷu tay', groupId: 'musculoskeletal' },
  { value: 'upper abdominal pain', labelVi: 'Đau bụng trên', groupId: 'gi' },
  { value: 'redness in ear', labelVi: 'Tai đỏ', groupId: 'ent' },
  { value: 'impotence', labelVi: 'Rối loạn cương', groupId: 'gu' },
  { value: 'acne or pimples', labelVi: 'Mụn trứng cá', groupId: 'dermatology' },
  { value: 'skin on leg or foot looks infected', labelVi: 'Da chân/bàn chân nghi nhiễm trùng', groupId: 'dermatology' },
  { value: 'itching of skin', labelVi: 'Ngứa da', groupId: 'dermatology' },
  { value: 'coughing up sputum', labelVi: 'Ho ra đờm', groupId: 'respiratory' },
  { value: 'allergic reaction', labelVi: 'Phản ứng dị ứng', groupId: 'dermatology' },
  { value: 'uterine contractions', labelVi: 'Cơn gò tử cung', groupId: 'gu' },
  { value: 'fluid retention', labelVi: 'Giữ nước/phù', groupId: 'cardio' },
  { value: 'symptoms of eye', labelVi: 'Triệu chứng ở mắt', groupId: 'ent' },
  { value: 'vomiting blood', labelVi: 'Nôn ra máu', groupId: 'gi' },
  { value: 'swollen or red tonsils', labelVi: 'Amidan sưng/đỏ', groupId: 'ent' },
  { value: 'itchiness of eye', labelVi: 'Ngứa mắt', groupId: 'ent' },
  { value: 'leg pain', labelVi: 'Đau chân', groupId: 'musculoskeletal' },
  { value: 'penile discharge', labelVi: 'Dịch chảy dương vật', groupId: 'gu' },
  { value: 'vaginal redness', labelVi: 'Âm đạo đỏ/viêm', groupId: 'gu' },
  { value: 'diaper rash', labelVi: 'Hăm tã', groupId: 'dermatology' },
  { value: 'fainting', labelVi: 'Ngất xỉu', groupId: 'cardio' },
  { value: 'rectal bleeding', labelVi: 'Chảy máu hậu môn', groupId: 'gi' },
  { value: 'decreased appetite', labelVi: 'Chán ăn', groupId: 'general' },
  { value: 'painful sinuses', labelVi: 'Đau xoang', groupId: 'ent' },
  { value: 'pulling at ears', labelVi: 'Kéo tai (khó chịu ở tai)', groupId: 'ent' },
  { value: 'decreased heart rate', labelVi: 'Nhịp tim chậm', groupId: 'cardio' },
  { value: 'shoulder stiffness or tightness', labelVi: 'Cứng vai', groupId: 'musculoskeletal' },
  { value: 'penis pain', labelVi: 'Đau dương vật', groupId: 'gu' },
  { value: 'throat redness', labelVi: 'Họng đỏ', groupId: 'ent' },
  { value: 'itchy scalp', labelVi: 'Ngứa da đầu', groupId: 'dermatology' },
  { value: 'heavy menstrual flow', labelVi: 'Ra kinh nhiều', groupId: 'gu' },
  { value: 'feeling ill', labelVi: 'Mệt mỏi/khó chịu', groupId: 'general' },
  { value: 'dry or flaky scalp', labelVi: 'Da đầu khô/tróc vảy', groupId: 'dermatology' },
  { value: 'neck swelling', labelVi: 'Cổ sưng', groupId: 'ent' },
  { value: 'symptoms of bladder', labelVi: 'Triệu chứng bàng quang', groupId: 'gu' },
  { value: 'eye burns or stings', labelVi: 'Cay rát mắt', groupId: 'ent' },
  { value: 'weakness', labelVi: 'Yếu mệt', groupId: 'general' },
  { value: 'fears and phobias', labelVi: 'Sợ hãi/ám ảnh', groupId: 'neuro' },
  { value: 'sneezing', labelVi: 'Hắt hơi', groupId: 'respiratory' },
  { value: 'knee swelling', labelVi: 'Sưng gối', groupId: 'musculoskeletal' },
  { value: 'neck mass', labelVi: 'Khối ở cổ', groupId: 'ent' },
  { value: 'leg swelling', labelVi: 'Phù chân', groupId: 'cardio' },
  { value: 'lymphedema', labelVi: 'Phù bạch huyết', groupId: 'cardio' },
  { value: 'depression', labelVi: 'Trầm cảm', groupId: 'neuro' },
  { value: 'painful menstruation', labelVi: 'Đau bụng kinh', groupId: 'gu' },
  { value: 'regurgitation', labelVi: 'Trào ngược', groupId: 'gi' },
  { value: 'mouth pain', labelVi: 'Đau miệng', groupId: 'ent' },
  { value: 'sweating', labelVi: 'Đổ mồ hôi', groupId: 'general' },
  { value: 'penis redness', labelVi: 'Đỏ dương vật', groupId: 'gu' },
  { value: 'fluid in ear', labelVi: 'Dịch trong tai', groupId: 'ent' },
  { value: 'eyelid swelling', labelVi: 'Sưng mí mắt', groupId: 'ent' },
  { value: 'antisocial behavior', labelVi: 'Hành vi chống đối', groupId: 'neuro' },
  { value: 'skin irritation', labelVi: 'Kích ứng da', groupId: 'dermatology' },
  { value: 'knee weakness', labelVi: 'Yếu gối', groupId: 'musculoskeletal' },
  { value: 'unpredictable menstruation', labelVi: 'Kinh nguyệt thất thường', groupId: 'gu' },
  { value: 'shoulder pain', labelVi: 'Đau vai', groupId: 'musculoskeletal' },
  { value: 'spotting or bleeding during pregnancy', labelVi: 'Rỉ máu khi mang thai', groupId: 'gu' },
];

export const symptomGroups: SymptomGroup[] = (Object.keys(groupMeta) as SymptomGroupId[]).map((groupId) => ({
  id: groupId,
  labelVi: groupMeta[groupId].labelVi,
  descriptionVi: groupMeta[groupId].descriptionVi,
  symptoms: rawSymptoms
    .filter((item) => item.groupId === groupId)
    .map(({ groupId: _groupId, labelEn, ...rest }) => ({
      ...rest,
      labelEn: labelEn ?? toTitleCase(rest.value),
    })),
}));
